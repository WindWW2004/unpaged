import { DurableObject } from "cloudflare:workers";

// Keep the size limits in sync with public/instructor.js
const MAX_PAGE_CHARS = 900_000;
const MAX_PAGES = 500;
const CHUNK = 1024 * 1024; // media is stored in 1 MB chunks
const MAX_ASSET_BYTES = 25 * CHUNK;
const MAX_DECK_BYTES = 200 * CHUNK;

const CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // no I, O, 0, 1
const MAX_QUIZZES = 20; // per page
const MAX_OPTIONS = 12; // per quiz
const MAX_VOTERS = 2000; // per page
const MAX_NOTES_CHARS = 20_000; // speaker notes, per page
const DECK_ID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;

const MIME = {
  png: "image/png", jpg: "image/jpeg", jpeg: "image/jpeg", gif: "image/gif", webp: "image/webp",
  avif: "image/avif", svg: "image/svg+xml", ico: "image/x-icon",
  mp4: "video/mp4", webm: "video/webm", mov: "video/quicktime", m4v: "video/mp4",
  mp3: "audio/mpeg", wav: "audio/wav", ogg: "audio/ogg", m4a: "audio/mp4",
  css: "text/css; charset=utf-8", js: "text/javascript; charset=utf-8", mjs: "text/javascript; charset=utf-8",
  json: "application/json", lottie: "application/zip", riv: "application/octet-stream", wasm: "application/wasm",
  woff: "font/woff", woff2: "font/woff2", ttf: "font/ttf", otf: "font/otf",
  txt: "text/plain; charset=utf-8", csv: "text/csv; charset=utf-8", vtt: "text/vtt; charset=utf-8",
  glb: "model/gltf-binary", gltf: "model/gltf+json",
};

const ASSET_HEADERS = {
  "Accept-Ranges": "bytes",
  "Cache-Control": "private, max-age=3600",
  // Slides run in an opaque-origin sandbox, so their fetch() calls are cross-origin.
  "Access-Control-Allow-Origin": "*",
  "X-Content-Type-Options": "nosniff",
  // If someone opens an uploaded file directly, it must not run as this site.
  "Content-Security-Policy": "sandbox",
};

const text = (body, status = 200) => new Response(body, { status });

const NAME = /^[a-z0-9][a-z0-9._@+-]{2,63}$/;
const CODE = /^[A-Z0-9]{6}$/;
const SESSION_MS = 90 * 24 * 3600 * 1000;
const MAX_FAILS = 10; // wrong passwords in a row before sign-in pauses
const LOCK_MS = 15 * 60 * 1000;

const bytes = (s) => new TextEncoder().encode(s);
const hex = (buf) => [...new Uint8Array(buf)].map((b) => b.toString(16).padStart(2, "0")).join("");
const sha256 = async (s) => hex(await crypto.subtle.digest("SHA-256", bytes(s)));
const randomHex = (n) => hex(crypto.getRandomValues(new Uint8Array(n)));

// Compare via digests so timing does not leak how much matched.
async function same(a, b) {
  const [x, y] = await Promise.all([crypto.subtle.digest("SHA-256", bytes(a)), crypto.subtle.digest("SHA-256", bytes(b))]);
  return crypto.subtle.timingSafeEqual(x, y);
}

async function passwordHash(password, salt) {
  const key = await crypto.subtle.importKey("raw", bytes(password), "PBKDF2", false, ["deriveBits"]);
  return hex(await crypto.subtle.deriveBits({ name: "PBKDF2", hash: "SHA-256", salt: bytes(salt), iterations: 100_000 }, key, 256));
}

// Invitation codes are shown as XXXXX-XXXXX and may be typed in any case, with or without the dash.
const inviteKey = (typed) => "invite:" + String(typed).toUpperCase().replace(/[^A-Z0-9]/g, "").slice(0, 40);
const inviteView = ({ code, note, created, usedBy }) => ({ code: code.slice(0, 5) + "-" + code.slice(5), note, created, usedBy: usedBy || null });

// Apps: an instructor's own Worker shown under this site's address, as /name/.
const APP_NAME = /^[a-z][a-z0-9-]{1,30}[a-z0-9]$/;
// Paths that belong to this site, now or plausibly later.
const RESERVED = new Set(["api", "apps", "instructor", "guide", "cdn-cgi", "index", "student", "students", "courses", "account", "login", "signup", "assets", "static", "admin"]);
const MAX_APPS = 5; // per instructor

// The address of someone's Worker as they typed it: its origin, or null if it is not usable.
function workerOrigin(typed) {
  try {
    const given = new URL(String(typed).trim());
    const local = given.hostname === "localhost" || given.hostname === "127.0.0.1";
    return given.protocol === "https:" || (local && given.protocol === "http:") ? given.origin : null;
  } catch {
    return null;
  }
}

const bearer = (request) => (request.headers.get("Authorization") || "").replace(/^Bearer /, "");

// Instructor accounts, their sign-in sessions, and the list of courses with who owns each.
//
// An instructor signs up with an invitation code. Each code makes one account. Signing up
// with INSTRUCTOR_KEY instead makes a site-owner account, which creates and withdraws the
// invitation codes and also owns the courses created before accounts existed.
//
// An instructor may keep their courses in their own copy of this Worker. The account then
// holds that Worker's address and its INSTRUCTOR_KEY, and each of those courses is listed
// here with the address, so this site can pass requests for it along.
//
// An instructor may also show a Worker of their own under this site's address ("apps", see
// the end of this file). The table of apps is kept here. An app is registered by its
// instructor, signed in or with their app token: a long-lived credential made for a deploy
// script, good for registering apps and nothing else.
//
// Paths under /public are reachable from the internet (as /api/...); the rest are called
// only by the Worker below.
export class Directory extends DurableObject {
  async fetch(request) {
    const url = new URL(request.url);
    const path = url.pathname;
    const method = request.method;
    const store = this.ctx.storage;

    if (path === "/auth") {
      const user = await this.userOf(await request.text());
      return user ? Response.json({ name: user.name, owner: Boolean(user.owner), worker: user.worker || null }) : text("sign in first", 401);
    }

    const where = path.match(/^\/where\/([A-Z0-9]{6})$/);
    if (where) {
      const course = await store.get(`course:${where[1]}`);
      return course ? Response.json(course) : text("unknown", 404);
    }

    if (path === "/courses" && method === "POST") {
      const course = await request.json();
      if (await store.get(`course:${course.code}`)) return text("exists", 409);
      await store.put(`course:${course.code}`, course);
      return text("ok");
    }

    // The courses of one instructor. ?all lists every course (the INSTRUCTOR_KEY itself).
    if (path === "/courses") {
      const name = url.searchParams.get("user");
      const user = name ? await store.get(`user:${name}`) : null;
      if (user?.worker) await this.importCourses(user);
      const all = [...(await store.list({ prefix: "course:" })).values()];
      const mine = url.searchParams.has("all") ? all : all.filter((c) => (c.owner ? c.owner === name : Boolean(user?.owner)));
      return Response.json(mine.sort((a, b) => b.created - a.created).map(({ code, title, created }) => ({ code, title, created })));
    }

    if (path === "/apps") return Response.json([...(await store.list({ prefix: "app:" })).values()]);

    if (!path.startsWith("/public/")) return text("not found", 404);
    const body = method === "GET" ? null : await request.json().catch(() => null);
    const str = (v) => (typeof v === "string" ? v : "");

    if (path === "/public/signup" && method === "POST") {
      const { INSTRUCTOR_KEY } = this.env;
      if (!INSTRUCTOR_KEY) return text("This site is not set up for accounts yet. See the README.", 503);
      const typed = str(body?.invite).trim();
      const owner = await same(typed, INSTRUCTOR_KEY);
      const wrong = () => text("That invitation code is not right, or it has been used.", 403);
      if (!owner && (await store.get(inviteKey(typed)))?.usedBy !== "") return wrong();
      const name = str(body?.name).trim().toLowerCase();
      const password = str(body?.password);
      if (!NAME.test(name)) return text("Use 3 to 64 letters, digits or . _ @ + - for the name.", 400);
      if (password.length < 8 || password.length > 200) return text("Use a password of at least 8 characters.", 400);
      const salt = randomHex(16);
      const hash = await passwordHash(password, salt);
      // From here to the last put there is nothing to wait for but storage, so two people
      // signing up at once cannot take the same name or the same code.
      if (await store.get(`user:${name}`)) return text("That name is taken.", 409);
      if (!owner) {
        const invite = await store.get(inviteKey(typed));
        if (invite?.usedBy !== "") return wrong();
        await store.put(inviteKey(typed), { ...invite, usedBy: name, usedAt: Date.now() });
      }
      await store.put(`user:${name}`, { name, salt, hash, owner, created: Date.now() });
      return Response.json({ name, token: await this.newSession(name) });
    }

    if (path === "/public/login" && method === "POST") {
      const name = str(body?.name).trim().toLowerCase();
      const user = NAME.test(name) ? await store.get(`user:${name}`) : null;
      if (user?.lockedUntil > Date.now()) return text("Too many wrong passwords. Try again in 15 minutes.", 429);
      // Hash even when there is no such account, so the reply takes as long either way.
      const hash = await passwordHash(str(body?.password), user?.salt || "none");
      if (!user || !(await same(hash, user.hash))) {
        if (user) {
          user.fails = (user.fails || 0) + 1;
          if (user.fails >= MAX_FAILS) Object.assign(user, { fails: 0, lockedUntil: Date.now() + LOCK_MS });
          await store.put(`user:${name}`, user);
        }
        return text("Wrong name or password.", 403);
      }
      if (user.fails || user.lockedUntil) await store.put(`user:${name}`, { ...user, fails: 0, lockedUntil: 0 });
      return Response.json({ name, token: await this.newSession(name) });
    }

    // The site owner sets a new password for someone who forgot theirs.
    if (path === "/public/account/password" && method === "POST" && body?.key !== undefined) {
      if (!this.env.INSTRUCTOR_KEY || !(await same(str(body.key), this.env.INSTRUCTOR_KEY))) return text("That key is not right.", 403);
      const target = await store.get(`user:${str(body.name).trim().toLowerCase()}`);
      if (!target) return text("No such account.", 404);
      if (str(body.password).length < 8 || str(body.password).length > 200) return text("Use a password of at least 8 characters.", 400);
      Object.assign(target, { hash: await passwordHash(body.password, target.salt), fails: 0, lockedUntil: 0 });
      await store.put(`user:${target.name}`, target);
      return text("ok");
    }

    const token = bearer(request);

    if (path === "/public/apps" || path.startsWith("/public/apps/")) {
      const by = (await this.userOf(token)) || (await this.userOfAppToken(token));
      if (!by) return text("sign in first", 401);
      return this.apps(request, path, body, by);
    }

    const user = await this.userOf(token);
    if (!user) return text("sign in first", 401);

    if (path === "/public/logout" && method === "POST") {
      await store.delete(`session:${await sha256(token)}`);
      return text("ok");
    }

    if (path === "/public/account" && method === "GET") {
      return Response.json({ name: user.name, owner: Boolean(user.owner), worker: user.worker?.origin || null, appToken: Boolean(user.appToken) });
    }

    // The app token. Making a new one ends the old one. It is shown once and kept as a digest.
    if (path === "/public/account/app-token" && (method === "POST" || method === "DELETE")) {
      if (user.appToken) await store.delete(`apptoken:${user.appToken}`);
      delete user.appToken;
      let made = null;
      if (method === "POST") {
        made = "uap_" + randomHex(24);
        user.appToken = await sha256(made);
        await store.put(`apptoken:${user.appToken}`, { name: user.name });
      }
      await store.put(`user:${user.name}`, user);
      return Response.json({ token: made });
    }

    // Invitation codes: the site-owner accounts create, list and withdraw them.
    if (path.startsWith("/public/invites")) {
      if (!user.owner) return text("only the site owner manages invitation codes", 403);
      if (path === "/public/invites" && method === "GET") {
        const all = [...(await store.list({ prefix: "invite:" })).values()];
        return Response.json(all.sort((a, b) => b.created - a.created).map(inviteView));
      }
      if (path === "/public/invites" && method === "POST") {
        if ((await store.list({ prefix: "invite:", limit: 1001 })).size > 1000) return text("Too many invitation codes. Withdraw some first.", 400);
        const code = [...crypto.getRandomValues(new Uint8Array(10))].map((n) => CODE_ALPHABET[n % 32]).join("");
        const invite = { code, note: str(body?.note).trim().slice(0, 80), created: Date.now(), by: user.name, usedBy: "" };
        await store.put(`invite:${code}`, invite);
        return Response.json(inviteView(invite));
      }
      // Withdrawing a used code only removes it from the list; the account stays.
      if (method === "DELETE") {
        await store.delete(inviteKey(decodeURIComponent(path.slice("/public/invites/".length))));
        return text("ok");
      }
      return text("not found", 404);
    }

    if (path === "/public/account/password" && method === "POST") {
      if (!(await same(await passwordHash(str(body?.current), user.salt), user.hash))) return text("The current password is not right.", 403);
      if (str(body?.password).length < 8 || str(body.password).length > 200) return text("Use a password of at least 8 characters.", 400);
      user.hash = await passwordHash(body.password, user.salt);
      await store.put(`user:${user.name}`, user);
      return text("ok");
    }

    if (path === "/public/account/worker" && method === "PUT") {
      const origin = workerOrigin(body?.origin);
      if (!origin) return text("Enter the address of your Worker, starting with https://", 400);
      if (origin === request.headers.get("X-Site-Origin")) return text("That is this site. Enter the address of your own Worker.", 400);
      const key = str(body?.key);
      user.worker = { origin, key };
      if ((await this.importCourses(user)) === null) return text("Could not sign in to that Worker with that key. Check both.", 400);
      await store.put(`user:${user.name}`, user);
      return Response.json({ name: user.name, worker: origin });
    }

    if (path === "/public/account/worker" && method === "DELETE") {
      delete user.worker;
      await store.put(`user:${user.name}`, user);
      return Response.json({ name: user.name, worker: null });
    }

    return text("not found", 404);
  }

  // GET /public/apps lists the instructor's apps (every app, for a site owner).
  // PUT /public/apps/name { origin } registers or updates one; DELETE removes it.
  async apps(request, path, body, user) {
    const store = this.ctx.storage;
    const method = request.method;
    const all = async () => [...(await store.list({ prefix: "app:" })).values()];
    const listing = async () => Response.json((await all()).filter((a) => user.owner || a.owner === user.name).sort((a, b) => a.name.localeCompare(b.name)));
    if (path === "/public/apps") return method === "GET" ? listing() : text("method not allowed", 405);

    const name = path.slice("/public/apps/".length);
    if (!APP_NAME.test(name)) return text("Use 3 to 32 lowercase letters, digits or hyphens for the name, starting with a letter.", 400);
    const current = await store.get(`app:${name}`);
    if (current && current.owner !== user.name && !user.owner) return text("That name is taken by another instructor.", 409);
    if (method === "DELETE") {
      await store.delete(`app:${name}`);
      return listing();
    }
    if (method !== "PUT") return text("method not allowed", 405);

    const origin = workerOrigin(body?.origin);
    if (!origin) return text("Give the address of your Worker as origin, starting with https://", 400);
    const site = request.headers.get("X-Site-Origin");
    if (origin === site) return text("That is this site. Give the address of your own Worker.", 400);
    // A name must not hide a page of this site.
    let taken = RESERVED.has(name);
    for (const page of [`/${name}`, `/${name}/`]) taken ||= (await this.env.ASSETS.fetch(new Request(site + page))).status !== 404;
    if (taken) return text("That name is used by the site. Choose another.", 400);
    if (!current && (await all()).filter((a) => a.owner === user.name).length >= MAX_APPS) return text(`You can register up to ${MAX_APPS} apps. Remove one first.`, 400);
    await store.put(`app:${name}`, { name, origin, owner: current?.owner || user.name, created: current?.created || Date.now(), updated: Date.now() });
    return listing();
  }

  async userOfAppToken(token) {
    if (!token.startsWith("uap_")) return null;
    const digest = await sha256(token);
    const held = await this.ctx.storage.get(`apptoken:${digest}`);
    const user = held ? await this.ctx.storage.get(`user:${held.name}`) : null;
    return user?.appToken === digest ? user : null;
  }

  async newSession(name) {
    const token = randomHex(32);
    await this.ctx.storage.put(`session:${await sha256(token)}`, { name, created: Date.now() });
    return token;
  }

  async userOf(token) {
    if (!token) return null;
    const key = `session:${await sha256(token)}`;
    const session = await this.ctx.storage.get(key);
    if (!session) return null;
    if (Date.now() - session.created > SESSION_MS) {
      await this.ctx.storage.delete(key);
      return null;
    }
    return (await this.ctx.storage.get(`user:${session.name}`)) || null;
  }

  // Lists the courses in an instructor's own Worker here, so that this site can find them.
  // Covers courses made there directly. Returns null if that Worker did not accept the key.
  async importCourses(user) {
    let list;
    try {
      const res = await fetch(`${user.worker.origin}/api/courses`, { headers: { Authorization: `Bearer ${user.worker.key}` } });
      list = res.ok ? await res.json() : null;
    } catch {
      list = null;
    }
    if (!Array.isArray(list)) return null;
    for (const c of list.slice(0, 1000)) {
      if (!CODE.test(c?.code || "") || (await this.ctx.storage.get(`course:${c.code}`))) continue;
      await this.ctx.storage.put(`course:${c.code}`, {
        code: c.code,
        title: String(c.title || "").slice(0, 120),
        created: Number(c.created) || Date.now(),
        owner: user.name,
        worker: user.worker.origin,
      });
    }
    return list.length;
  }
}

// One Course per course code. It stores the course's decks (pages + media) and runs
// the live class: while a deck is live, students only ever receive the current page.
//
// A quiz page is stored without its answers. The answer key (which options are correct, and
// the explanation) is kept apart from the page and is sent to students only once the
// instructor reveals it, or once the deck is released.
//
// Speaker notes are also stored apart from the page, and are only ever sent to the instructor.
//
// Deck status: "draft" (uploaded, not taught) -> "live" (being taught) -> "released"
// (taught; students may browse it freely).
export class Course extends DurableObject {
  constructor(ctx, env) {
    super(ctx, env);
    // Answered by the runtime without waking the object, so idle courses stay hibernated.
    ctx.setWebSocketAutoResponse(new WebSocketRequestResponsePair("ping", "pong"));
  }

  // The Worker has already checked who is signed in and says so in X-User (an account's name)
  // or X-Admin (the INSTRUCTOR_KEY itself). The instructor of a course is the account that
  // created it. A course from before accounts existed belongs to the site-owner accounts.
  // Only the instructor may change anything, except POST /answer: a student answering a quiz.
  async fetch(request) {
    const url = new URL(request.url);
    const path = url.pathname;
    const method = request.method;
    const store = this.ctx.storage;
    const course = await store.get("course");

    if (path === "/init") {
      if (course) return text("exists", 409);
      const { title, owner } = await request.json();
      await store.put("course", { title, owner, created: Date.now() });
      await store.put("decks", []);
      return text("ok");
    }
    if (!course) return text("no such course", 404);

    const who = request.headers.get("X-User");
    const instructor =
      request.headers.get("X-Admin") === "1" ||
      (who !== null && (course.owner ? course.owner === who : request.headers.get("X-Owner") === "1"));
    if (method !== "GET" && path !== "/answer" && !instructor) return text("not your course", 403);

    const decks = (await store.get("decks")) || [];
    const live = (await store.get("live")) || null;

    if (path === "/" && method === "GET") {
      return Response.json({
        title: course.title,
        mine: instructor, // whether the person asking may teach this course
        live: live && (instructor ? live : { deckId: live.deckId }),
        decks: decks.map((d) => ({
          // Students do not get the id of a deck that has not been taught yet.
          id: instructor || d.status !== "draft" ? d.id : undefined,
          title: d.title,
          pages: d.pages.length,
          status: d.status,
        })),
      });
    }

    if (path === "/ws") {
      const { 0: client, 1: server } = new WebSocketPair();
      // The role only decides who is counted as a student; it grants nothing.
      this.ctx.acceptWebSocket(server, [url.searchParams.get("role") === "instructor" ? "instructor" : "student"]);
      if (live) server.send(await this.slideMessage(decks, live));
      this.sendCount();
      return new Response(null, { status: 101, webSocket: client });
    }

    if (path === "/decks" && method === "POST") {
      const body = await request.json().catch(() => null);
      const pages = body?.pages;
      if (!body || !DECK_ID.test(body.id) || decks.some((d) => d.id === body.id)) return text("bad deck id", 400);
      if (!Array.isArray(pages) || !pages.length || pages.length > MAX_PAGES) return text("bad page count", 400);
      if (pages.some((p) => typeof p.html !== "string" || p.html.length > MAX_PAGE_CHARS)) return text("page too large", 413);
      const keys = pages.map((p) => quizKey(p.quiz));
      if (keys.includes(null)) return text("bad quiz", 400);
      const notes = pages.map((p) => p.notes ?? "");
      if (notes.some((n) => typeof n !== "string" || n.length > MAX_NOTES_CHARS)) return text("notes too long", 413);
      for (let i = 0; i < pages.length; i++) {
        await store.put(`page:${body.id}:${i}`, pages[i].html);
        if (keys[i].length) await store.put(`quiz:${body.id}:${i}`, keys[i]);
        if (notes[i]) await store.put(`notes:${body.id}:${i}`, notes[i]);
      }
      decks.push({
        id: body.id,
        title: String(body.title || "Untitled deck").slice(0, 200),
        pages: pages.map((p, i) => String(p.title || `Page ${i + 1}`).slice(0, 200)),
        status: "draft",
        notes: notes.some(Boolean),
        created: Date.now(),
      });
      await store.put("decks", decks);
      this.broadcast({ type: "decks" });
      return text("ok");
    }

    if (path === "/goto" && method === "POST") {
      if (!live) return text("no class is live", 409);
      const deck = decks.find((d) => d.id === live.deckId);
      const body = await request.json();
      const index = Number(body.index);
      if (!Number.isInteger(index) || index < 0 || index >= deck.pages.length) return text("bad page", 400);
      live.index = index;
      await store.put("live", live);
      this.broadcastRaw(await this.slideMessage(decks, live), "student");
      // The instructor's other windows (a projector window, a phone used as a remote) follow too.
      // "from" lets the window that turned the page ignore its own echo.
      this.broadcastRaw(JSON.stringify({ type: "goto", deckId: live.deckId, index, from: String(body.from || "").slice(0, 40) }), "instructor");
      return text("ok");
    }

    // A student picks an option on the page that is live right now. One answer per student
    // per quiz; picking again replaces it, until the instructor reveals the answers.
    if (path === "/answer" && method === "POST") {
      const body = await request.json().catch(() => null);
      if (!live || !body || body.deckId !== live.deckId || body.page !== live.index) return text("that page is not live", 409);
      if (live.revealed?.includes(live.index)) return text("answers are already shown", 409);
      const key = await store.get(`quiz:${live.deckId}:${live.index}`);
      const { quiz, option, voter } = body;
      if (!key || !Number.isInteger(quiz) || !key[quiz] || !Number.isInteger(option) || option < 0 || option >= key[quiz].options) return text("bad answer", 400);
      if (typeof voter !== "string" || !DECK_ID.test(voter)) return text("bad answer", 400);
      const votesKey = `votes:${live.deckId}:${live.index}`;
      const votes = (await store.get(votesKey)) || {};
      if (!votes[voter] && Object.keys(votes).length >= MAX_VOTERS) return text("too many answers", 429);
      (votes[voter] ||= [])[quiz] = option;
      await store.put(votesKey, votes);
      // The instructor's screen only gets a head count: who chose what stays hidden until the reveal.
      this.broadcastRaw(JSON.stringify({ type: "answered", page: live.index, count: Object.keys(votes).length }), "instructor");
      return text("ok");
    }

    // The instructor shows the answers for the page that is live.
    if (path === "/reveal" && method === "POST") {
      if (!live) return text("no class is live", 409);
      const results = await this.quizResults(live.deckId, live.index);
      if (!results) return text("this page has no quiz", 400);
      live.revealed = [...new Set([...(live.revealed || []), live.index])];
      await store.put("live", live);
      this.broadcast({ type: "reveal", deckId: live.deckId, page: live.index, results });
      return Response.json(results);
    }

    if (path === "/end" && method === "POST") {
      if (!live) return text("ok");
      decks.find((d) => d.id === live.deckId).status = "released";
      await store.put("decks", decks);
      await store.delete("live");
      this.broadcast({ type: "end", deckId: live.deckId, index: live.index });
      return text("ok");
    }

    const m = path.match(/^\/decks\/([0-9a-f-]{36})(\/.*)?$/);
    if (!m) return text("not found", 404);
    const [, id, rest = ""] = m;
    const deck = decks.find((d) => d.id === id);

    // Media. Uploaded before the deck record exists, so it is not tied to `deck`.
    if (rest.startsWith("/assets/")) {
      let file;
      try {
        file = decodeURIComponent(rest.slice("/assets/".length)).normalize("NFC");
      } catch {
        return text("bad path", 400);
      }
      if (!file || file.length > 512 || file.split("/").some((s) => s === "" || s === "." || s === "..")) return text("bad path", 400);
      if (method === "PUT") return this.putAsset(id, file, request);
      if (method === "GET") return this.getAsset(id, file, request);
      return text("method not allowed", 405);
    }

    if (!deck) return text("no such deck", 404);
    const canRead = instructor || deck.status === "released";

    if (rest === "" && method === "GET") {
      if (!canRead) return text("this deck is not open yet", 403);
      const out = { id, title: deck.title, status: deck.status, pages: deck.pages };
      if (instructor) out.notes = Boolean(deck.notes); // whether any page has speaker notes
      return Response.json(out);
    }

    if (rest === "" && method === "DELETE") {
      if (live?.deckId === id) return text("end the class first", 409);
      for (const prefix of [`page:${id}:`, `quiz:${id}:`, `notes:${id}:`, `votes:${id}:`, `asset:${id}/`, `chunk:${id}/`]) {
        for (;;) {
          const keys = [...(await store.list({ prefix, limit: 128 })).keys()];
          if (!keys.length) break;
          await store.delete(keys);
        }
      }
      await store.delete(`bytes:${id}`);
      await store.put("decks", decks.filter((d) => d.id !== id));
      this.broadcast({ type: "decks" });
      return text("ok");
    }

    const page = rest.match(/^\/pages\/(\d+)$/);
    if (page && method === "GET") {
      if (!canRead) return text("this deck is not open yet", 403);
      const html = await store.get(`page:${id}:${page[1]}`);
      if (html === undefined) return text("no such page", 404);
      return new Response(html, { headers: { "Content-Type": "text/plain; charset=utf-8", "Cache-Control": "no-store" } });
    }

    // The quiz on a page. The instructor always gets the answer key, plus how the class answered
    // while the deck is live. Students get the key once the deck is released.
    const quizPage = rest.match(/^\/pages\/(\d+)\/quiz$/);
    if (quizPage && method === "GET") {
      if (!canRead) return text("this deck is not open yet", 403);
      const n = Number(quizPage[1]);
      const key = (await store.get(`quiz:${id}:${n}`)) || [];
      const out = { key };
      if (instructor && live?.deckId === id && key.length) {
        out.answered = Object.keys((await store.get(`votes:${id}:${n}`)) || {}).length;
        out.revealed = Boolean(live.revealed?.includes(n));
        if (out.revealed) out.results = await this.quizResults(id, n);
      }
      return Response.json(out, { headers: { "Cache-Control": "no-store" } });
    }

    // Speaker notes for a page. Instructor only, whatever the deck's status.
    const notesPage = rest.match(/^\/pages\/(\d+)\/notes$/);
    if (notesPage && method === "GET") {
      if (!instructor) return text("instructor only", 403);
      const notes = (await store.get(`notes:${id}:${notesPage[1]}`)) || "";
      return new Response(notes, { headers: { "Content-Type": "text/plain; charset=utf-8", "Cache-Control": "no-store" } });
    }

    if (rest === "/start" && method === "POST") {
      if (live && live.deckId !== id) return text("another class is live in this course", 409);
      if (live) return Response.json(live); // resuming
      const started = { deckId: id, index: 0, revealed: [] };
      // A new class starts with no answers from the last time this deck was taught.
      const old = [...(await store.list({ prefix: `votes:${id}:` })).keys()];
      if (old.length) await store.delete(old);
      deck.status = "live";
      await store.put("decks", decks);
      await store.put("live", started);
      this.broadcastRaw(await this.slideMessage(decks, started), "student");
      this.broadcast({ type: "decks" });
      return Response.json(started);
    }

    return text("not found", 404);
  }

  async slideMessage(decks, live) {
    const deck = decks.find((d) => d.id === live.deckId);
    return JSON.stringify({
      type: "slide",
      deckId: deck.id,
      title: deck.title,
      index: live.index,
      total: deck.pages.length,
      html: await this.ctx.storage.get(`page:${deck.id}:${live.index}`),
      // Present only when the answers on this page have been shown.
      results: live.revealed?.includes(live.index) ? await this.quizResults(deck.id, live.index) : null,
    });
  }

  // Per quiz on the page: the correct options, the explanation, and how many chose each option.
  async quizResults(id, index) {
    const key = await this.ctx.storage.get(`quiz:${id}:${index}`);
    if (!key) return null;
    const votes = Object.values((await this.ctx.storage.get(`votes:${id}:${index}`)) || {});
    return key.map((q, i) => {
      const counts = new Array(q.options).fill(0);
      for (const v of votes) if (Number.isInteger(v[i])) counts[v[i]]++;
      return { correct: q.correct, explain: q.explain, counts };
    });
  }

  async putAsset(id, file, request) {
    const store = this.ctx.storage;
    const key = `${id}/${file}`;
    if (Number(request.headers.get("Content-Length")) > MAX_ASSET_BYTES) return text("file too large", 413);
    const bytes = new Uint8Array(await request.arrayBuffer());
    if (bytes.length > MAX_ASSET_BYTES) return text("file too large", 413);

    const prev = await store.get(`asset:${key}`);
    const used = ((await store.get(`bytes:${id}`)) || 0) - (prev?.size || 0) + bytes.length;
    if (used > MAX_DECK_BYTES) return text("deck too large", 413);

    const chunks = Math.ceil(bytes.length / CHUNK);
    for (let i = 0; i < chunks; i++) await store.put(`chunk:${key}:${i}`, bytes.slice(i * CHUNK, (i + 1) * CHUNK));
    for (let i = chunks; i < (prev?.chunks || 0); i++) await store.delete(`chunk:${key}:${i}`);
    await store.put(`asset:${key}`, { size: bytes.length, chunks });
    await store.put(`bytes:${id}`, used);
    return text("ok");
  }

  async getAsset(id, file, request) {
    const store = this.ctx.storage;
    const key = `${id}/${file}`;
    const meta = await store.get(`asset:${key}`);
    if (!meta) return text("not found", 404);

    const ext = file.slice(file.lastIndexOf(".") + 1).toLowerCase();
    const headers = { ...ASSET_HEADERS, "Content-Type": MIME[ext] || "application/octet-stream" };

    // Range support: Safari will not play video without it.
    let start = 0;
    let end = meta.size - 1;
    let status = 200;
    const r = /^bytes=(\d*)-(\d*)$/.exec(request.headers.get("Range") || "");
    if (r && (r[1] || r[2]) && meta.size > 0) {
      if (r[1] === "") {
        start = Math.max(0, meta.size - Number(r[2]));
      } else {
        start = Number(r[1]);
        if (r[2]) end = Math.min(end, Number(r[2]));
      }
      if (start > end) {
        return new Response(null, { status: 416, headers: { ...headers, "Content-Range": `bytes */${meta.size}` } });
      }
      status = 206;
      headers["Content-Range"] = `bytes ${start}-${end}/${meta.size}`;
    }

    const body = new Uint8Array(end - start + 1);
    const first = Math.floor(start / CHUNK);
    const last = Math.floor(end / CHUNK);
    for (let i = first; i <= last; i++) {
      const chunk = await store.get(`chunk:${key}:${i}`);
      const from = i === first ? start - first * CHUNK : 0;
      const to = i === last ? end - last * CHUNK + 1 : chunk.length;
      body.set(chunk.subarray(from, to), i * CHUNK + from - start);
    }
    return new Response(body, { status, headers });
  }

  // Sockets are receive-only: page turns arrive over HTTP from the signed-in instructor.
  async webSocketMessage() {}

  async webSocketClose(ws, code) {
    try {
      ws.close(code === 1005 || code === 1006 ? 1000 : code);
    } catch {}
    this.sendCount(ws);
  }

  async webSocketError(ws) {
    this.sendCount(ws);
  }

  broadcast(msg) {
    this.broadcastRaw(JSON.stringify(msg));
  }

  broadcastRaw(data, tag) {
    for (const s of this.ctx.getWebSockets(tag)) {
      try {
        s.send(data);
      } catch {}
    }
  }

  sendCount(leaving) {
    const students = this.ctx.getWebSockets("student").filter((s) => s !== leaving).length;
    const data = JSON.stringify({ type: "count", students });
    for (const s of this.ctx.getWebSockets("instructor")) {
      if (s === leaving) continue;
      try {
        s.send(data);
      } catch {}
    }
  }
}

// Checks the answer key uploaded with a page. Returns [] for a page without a quiz, null if malformed.
function quizKey(quiz) {
  if (quiz === undefined) return [];
  if (!Array.isArray(quiz) || quiz.length > MAX_QUIZZES) return null;
  const key = [];
  for (const q of quiz) {
    const n = q?.options;
    if (!Number.isInteger(n) || n < 1 || n > MAX_OPTIONS || !Array.isArray(q.correct)) return null;
    if (q.correct.some((c) => !Number.isInteger(c) || c < 0 || c >= n)) return null;
    key.push({ options: n, correct: q.correct, explain: String(q.explain || "").slice(0, 20_000) });
  }
  return key;
}

const courseStub = (env, code) => env.COURSE.get(env.COURSE.idFromName(code));
const directory = (env) => env.DIRECTORY.get(env.DIRECTORY.idFromName("all"));

// Who is asking: null, an account ({ name, owner, worker }), or the INSTRUCTOR_KEY itself
// ({ admin: true }), which may manage every course in this Worker. Another site uses the key
// this way when an instructor keeps their courses here.
async function userOf(request, env) {
  const given = bearer(request);
  if (!given) return null;
  if (env.INSTRUCTOR_KEY && (await same(given, env.INSTRUCTOR_KEY))) return { admin: true };
  const res = await directory(env).fetch("https://directory/auth", { method: "POST", body: given });
  return res.ok ? res.json() : null;
}

// Where each course is kept: { owner, worker }, worker being the address of the owner's own
// Worker when it is not stored here. This never changes for a course, so it is kept per isolate.
const homes = new Map();
async function homeOf(env, code) {
  if (homes.has(code)) return homes.get(code);
  const res = await directory(env).fetch(`https://directory/where/${code}`);
  if (!res.ok) return null; // not remembered: the code may be given to a course later
  const { owner, worker } = await res.json();
  homes.set(code, { owner, worker });
  return homes.get(code);
}

// The apps table, name -> { origin, owner }. It is read for every path that could be an app,
// so each isolate keeps a copy for half a minute; a change shows everywhere within that time.
let appTable = null;
let appsLoaded = 0;
async function appsOf(env) {
  if (!appTable || Date.now() - appsLoaded > 30_000) {
    const list = await (await directory(env).fetch("https://directory/apps")).json();
    appTable = new Map(list.map((a) => [a.name, a]));
    appsLoaded = Date.now();
  }
  return appTable;
}

// Passes a request for /name/... to the app's Worker, which sees the same path.
async function toApp(request, url, app) {
  if (request.headers.has("X-Unpaged-App")) return text("This app points back at the site.", 508);
  if (url.pathname === `/${app.name}`) return Response.redirect(`${url.origin}/${app.name}/${url.search}`, 308);
  const out = new Request(app.origin + url.pathname + url.search, request);
  out.headers.set("X-Unpaged-App", "1");
  out.headers.set("X-Forwarded-Host", url.host);
  out.headers.set("X-Forwarded-Proto", url.protocol.slice(0, -1));
  let res;
  try {
    res = await fetch(out, { redirect: "manual" });
  } catch {
    return text("This app is not reachable right now. Try again shortly.", 502);
  }
  // A redirect that names the app's own address is sent to the same path on this site.
  const to = res.headers.get("Location");
  if (to?.startsWith(app.origin + "/")) {
    res = new Response(res.body, res);
    res.headers.set("Location", url.origin + to.slice(app.origin.length));
  }
  return res;
}

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    if (/^\/api\/(signup|login|logout|account(\/worker|\/password|\/app-token)?|invites(\/[A-Za-z0-9-]{1,40})?|apps(\/[^/]{1,64})?)$/.test(url.pathname)) {
      const out = new Request("https://directory/public" + url.pathname.slice("/api".length), request);
      out.headers.set("X-Site-Origin", url.origin);
      if (url.pathname.startsWith("/api/apps/")) appsLoaded = 0; // read the table again after a change
      return directory(env).fetch(out);
    }
    const m = url.pathname.match(/^\/api\/courses(?:\/([A-Za-z0-9]{6})(\/.*)?)?$/);
    if (!m) {
      const first = url.pathname.split("/", 2)[1];
      const app = APP_NAME.test(first) && !RESERVED.has(first) ? (await appsOf(env)).get(first) : null;
      return app ? toApp(request, url, app) : env.ASSETS.fetch(request);
    }
    const code = m[1]?.toUpperCase();
    const sub = m[2] || "/";
    if (sub === "/init") return text("not found", 404);

    const user = await userOf(request, env);
    // Students may read, and answer the quiz on the live page. Everything else, and the course
    // list, needs a signed-in instructor. The Course then checks that the course is theirs.
    const answering = Boolean(code) && request.method === "POST" && sub === "/answer";
    if (((request.method !== "GET" && !answering) || !code) && !user) return text("sign in first", 401);

    if (!code) {
      if (request.method === "GET") {
        return directory(env).fetch(`https://directory/courses?${user.admin ? "all" : "user=" + encodeURIComponent(user.name)}`);
      }
      if (request.method !== "POST") return text("method not allowed", 405);
      const title = String((await request.json().catch(() => ({}))).title || "").trim().slice(0, 120);
      if (!title) return text("a course needs a title", 400);
      const register = (course) => directory(env).fetch("https://directory/courses", { method: "POST", body: JSON.stringify(course) });

      // An instructor with their own Worker: the course is created there and listed here.
      if (user.worker) {
        const { origin, key } = user.worker;
        const res = await fetch(`${origin}/api/courses`, { method: "POST", headers: { Authorization: `Bearer ${key}` }, body: JSON.stringify({ title }) }).catch(() => null);
        const made = res?.ok ? await res.json().catch(() => null) : null;
        if (!CODE.test(made?.code || "")) return text("Your own Worker did not create the course. Check its address and key.", 502);
        const course = { code: made.code, title, created: Number(made.created) || Date.now() };
        if (!(await register({ ...course, owner: user.name, worker: origin })).ok) return text("That course code is already in use here. Try again.", 502);
        return Response.json(course);
      }

      for (let attempt = 0; attempt < 8; attempt++) {
        const candidate = [...crypto.getRandomValues(new Uint8Array(6))].map((n) => CODE_ALPHABET[n % 32]).join("");
        const course = { code: candidate, title, created: Date.now() };
        // Listed first: a code already listed here (for a course kept elsewhere) is skipped.
        if (!(await register({ ...course, owner: user.name })).ok) continue;
        const res = await courseStub(env, candidate).fetch("https://course/init", { method: "POST", body: JSON.stringify({ title, owner: user.name }) });
        if (!res.ok) continue;
        return Response.json(course);
      }
      return text("could not allocate a course code", 503);
    }

    // A course kept in its instructor's own Worker. That Worker's key goes along only for
    // the instructor who owns the course; nobody's sign-in here is ever sent on.
    const home = await homeOf(env, code);
    if (home?.worker) {
      const mine = Boolean(user?.name) && user.name === home.owner && user.worker?.origin === home.worker;
      if (request.method !== "GET" && !answering && !mine) return text("not your course", 403);
      const out = new Request(home.worker + url.pathname + url.search, request);
      out.headers.delete("Authorization");
      if (mine) out.headers.set("Authorization", `Bearer ${user.worker.key}`);
      return fetch(out);
    }

    if (sub === "/ws" && request.headers.get("Upgrade") !== "websocket") return text("expected websocket", 426);
    const forwarded = new Request("https://course" + sub + url.search, request);
    for (const h of ["X-User", "X-Owner", "X-Admin"]) forwarded.headers.delete(h);
    if (user?.admin) forwarded.headers.set("X-Admin", "1");
    else if (user) {
      forwarded.headers.set("X-User", user.name);
      if (user.owner) forwarded.headers.set("X-Owner", "1");
    }
    return courseStub(env, code).fetch(forwarded);
  },
};
