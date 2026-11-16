import { DurableObject } from "cloudflare:workers";
import unpaged from "./worker.js";

export { Course, Directory } from "./worker.js";

const prefix = "/servicerequest";
const port = 8080;

export class ServiceRequestContainer extends DurableObject {
  starting;

  async fetch(request) {
    this.starting ??= this.start().finally(() => {
      this.starting = undefined;
    });
    await this.starting;

    const url = new URL(request.url);
    url.protocol = "http:";
    url.host = "container";
    const headers = new Headers(request.headers);
    // Unpaged authentication and cookies belong only to the Unpaged handler.
    const session = (headers.get("cookie") ?? "").split(";")
      .map(cookie => cookie.trim())
      .find(cookie => cookie.startsWith("SERVICEREQUESTSESSION="));
    for (const name of ["host", "cookie", "authorization"]) headers.delete(name);
    if (session) headers.set("cookie", session);
    const proxyRequest = new Request(new Request(url, request), {
      headers,
      redirect: "manual",
    });
    return this.ctx.container.getTcpPort(port).fetch(proxyRequest);
  }

  async start() {
    const container = this.ctx.container;
    if (!container.running) {
      container.start({
        image: container.images.base,
        instance: "basic",
        enableInternet: false,
      });
    }
    await container.setInactivityTimeout(5 * 60 * 1000);

    const deadline = Date.now() + 60_000;
    const http = container.getTcpPort(port);
    while (Date.now() < deadline) {
      try {
        const response = await http.fetch(`http://container${prefix}/`, {
          signal: AbortSignal.timeout(1000),
        });
        await response.body?.cancel();
        if (response.ok) return;
      } catch {
        // Retry only the readiness probe, never the original request.
      }
      await scheduler.wait(300);
    }
    throw new Error("ServiceRequest did not become ready on port 8080");
  }
}

export default {
  async fetch(request, env, ctx) {
    const url = new URL(request.url);
    if (url.pathname === prefix) {
      url.pathname += "/";
      return Response.redirect(url, 308);
    }
    if (!url.pathname.startsWith(prefix + "/")) {
      return unpaged.fetch(request, env, ctx);
    }
    try {
      return await env.SERVICE_REQUEST.getByName("demo").fetch(request);
    } catch (error) {
      console.error("ServiceRequest container:", error);
      return new Response("ServiceRequest is unavailable. Please try again shortly.", { status: 503 });
    }
  },
};
