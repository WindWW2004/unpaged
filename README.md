# Unpaged

A Social Shields project. Interactive HTML decks in place of PDF slides, organized by course.

- Instructors each have an account. An instructor creates a course and adds decks to it.
- During a class the instructor turns the pages and every student's screen follows. Students can explore the current page but cannot change pages.
- When the instructor ends the class, the deck opens to students, who can come back at any time and browse it on their own.

## Run

```sh
npm install
npm run dev      # local: http://localhost:8787
npm run deploy   # deploy to Cloudflare (asks you to log in the first time)
```

Set the site owner's key once (use a long random one, and keep it to yourself): `npx wrangler secret put INSTRUCTOR_KEY`. For local development it is read from `.dev.vars` (`INSTRUCTOR_KEY=...`). Without it nobody can create an account.

- Instructors: `/instructor` (create an account or sign in, create a course, add decks, teach). The guide at `/guide` covers deck format and preparing a class.
- Students: enter the course code on the home page, or open `/?c=CODE`.

## Instructor accounts

The site owner creates the first account: open `/instructor`, choose **Create an account**, and enter `INSTRUCTOR_KEY` as the invitation code. That account also owns the courses created before accounts existed.

Other instructors join with an invitation code. The site owner creates codes under **Account > Invitation codes** on the course list, with a note on who each is for, and sends the instructor the link (**Copy link**) or the code. Each code makes one account. The list shows which codes have been used and by whom; a code that has not been used can be withdrawn.

With the link open, the instructor chooses a name and a password. From then on they manage their own courses without anyone's help. Each instructor sees and can change only their own courses.

There is no email, so a forgotten password is reset by the site owner:

```sh
curl -X POST https://YOUR-SITE/api/account/password \
  -d '{"name":"their-name","password":"a-new-password","key":"YOUR_INSTRUCTOR_KEY"}'
```

`INSTRUCTOR_KEY` also works by itself as a credential for the API (`Authorization: Bearer ...`), with access to every course in the Worker.

## An instructor with their own Worker

By default every instructor's courses are stored in this Worker. An instructor who wants their decks in their own Cloudflare account can arrange it without the site owner:

1. Deploy this repository to their own account (`npm install`, `npm run deploy`) and set its `INSTRUCTOR_KEY` (`npx wrangler secret put INSTRUCTOR_KEY`).
2. On this site, under **Account** on the course list, open **Keep my courses in my own Worker** and enter that Worker's address and its `INSTRUCTOR_KEY`.

Courses they create from then on are stored in their Worker, and the courses already in it are listed too. Courses created here earlier stay here. Students and the instructor keep using this site's address: this Worker passes requests for those courses along. The instructor's key is kept with their account here, since this Worker needs it to act for them. Requests still pass through this Worker and count toward this account's request limits; the storage is theirs. Their copy should be kept at the same version as this one, because the pages everyone loads come from here.

## An instructor's own app under this site's address

An instructor can show a Worker of their own on this site, as `https://SITE/name/`, without the site owner doing anything. The Worker registers itself through an API each time it is deployed, and this Worker passes requests for `/name/...` along to it.

1. On the course list, under **Account > Show my own app on this site**, create a token. It is shown once. It can register apps and nothing else; replace or revoke it there at any time.
2. Add the registration to the end of the app's deploy command, in `package.json` or in the Workers Builds deploy command:
   ```sh
   wrangler deploy && curl -fsS -X PUT https://SITE/api/apps/NAME \
     -H "Authorization: Bearer TOKEN" \
     -d '{"origin":"https://YOUR-WORKER.workers.dev"}'
   ```
   Registering again with the same name is harmless and updates the address. Within half a minute `https://SITE/NAME/` shows the app.

| Request | Does |
| --- | --- |
| `PUT /api/apps/NAME` with `{"origin": "https://..."}` | registers the app, or updates its address |
| `DELETE /api/apps/NAME` | stops showing it |
| `GET /api/apps` | lists your apps (every app, for a site owner) |

Each takes `Authorization: Bearer` with the app token. A name is 3 to 32 lowercase letters, digits or hyphens, starting with a letter. It cannot be a name the site uses itself (`api`, `instructor`, `guide` and so on) or one another instructor has registered. Each instructor can register up to 5 apps. Apps are also listed, and can be removed, under **Account**; a site owner sees all of them.

What to know:

- The app receives the same path the browser asked for, so it must serve its pages under `/NAME/`.
- Apps share this site's origin. A page of a registered app can read what Unpaged keeps in the browser, including an instructor's sign-in, and act as that instructor. Give invitation codes only to people you trust with that.
- Requests to an app pass through this Worker and count toward this account's request limits.
- A redirect that names the app's own `workers.dev` address is rewritten to the same path on this site. Cookies are passed through unchanged.

The ServiceRequest demo below can be shown this way, as `/servicerequest/`.

## ServiceRequest test deployment

The Spring Boot demo is mounted at `/servicerequest/` in the test Worker. The original Unpaged home page and course routes use the existing Worker code. The preview has its own Durable Objects and does not use the live site's course data or hostname.

1. In Cloudflare **Workers & Pages**, create a Worker from the GitHub fork, named `unpaged-servicerequest-test`. Cloudflare Containers requires Workers Paid.
2. In its **Settings > Builds**, use `servicerequest-preview` as this test Worker's production branch and the repository root as its build root. Use `npm run deploy:servicerequest` as the deploy command. Workers Builds installs the locked Wrangler dependencies and builds the Dockerfile during deployment.
3. Once the deployment and container rollout are ready, open the Worker's `workers.dev` URL with `/servicerequest/` appended. Test the dashboard, CSS, and a sample form submission. The JAR is built in Docker; do not upload `target/` or a JAR.

If you have Docker locally, you can also run `npm ci` and `npm run deploy:servicerequest` from the repository root after signing in to Wrangler. To run only the Java image locally, use `docker build -t unpaged-servicerequest .` and `docker run --rm -p 127.0.0.1:8080:8080 unpaged-servicerequest`, then open `http://localhost:8080/servicerequest/`.

Use demo data only. The app has no user sign-in, the H2 console is disabled, and in-memory H2 data resets when the container stops.

To publish under the shared hostname later, the Unpaged maintainer must review and update the existing Unpaged Worker's configuration. Keep its current Worker name, bindings, and migration history. Change its entry point to `src/servicerequest-preview.js`, add the container and its binding, and append a migration with a new tag for `ServiceRequestContainer`. Keep the existing hostname configuration so the app uses `/servicerequest/` on the same site. This branch does not change the existing `wrangler.jsonc`.

## Deck format

One HTML file, optionally with media files next to it. Each page is a `<section class="slide">`. A clickable knowledge point is a `.kp`; its details go in a nested `.detail`, which opens in a dialog on click. A quiz is a `.quiz` holding `.option` elements, the right one marked `data-correct`, with an optional `.explain`. Speaker notes for a page go in an `<aside class="notes">`. Media is referenced by relative path. Add a deck as a single `.html`, a `.zip`, or a folder. `public/guide.html` has the full description and `public/sample-deck.html` is a working example.

## Generating decks with Claude

`skills/unpaged-deck/` is a Claude skill that writes decks in this format and checks them before packaging. In Claude Code, copy the folder to `~/.claude/skills/`. In the Claude apps, upload `skills/unpaged-deck.skill`. To check a deck by hand: `python3 skills/unpaged-deck/scripts/check_deck.py path/to/deck-folder`.

## How it works

- `src/worker.js`: a Cloudflare Worker and two Durable Object classes.
  - `Course`, one per course code, stores the course's decks (each page as its own HTML document, media in 1 MB chunks) and runs the live class over WebSockets.
  - `Directory` keeps the instructor accounts, their sign-in sessions, and the list of courses with the owner of each.
- Deck status goes `draft` → `live` → `released`. Students can fetch a deck's pages only once it is `released`. While it is `live` they are sent the current page and nothing else.
- Students only read, with one exception: answering a quiz on the live page. Every other write, including page turns, is an HTTP request carrying the instructor's session. The Worker checks who is signed in, and the course checks that it belongs to them.
- Quiz answers are taken out of the page when a deck is added and stored apart from it. Students receive them when the instructor shows the answers, or once the deck is released. Answers are counted per anonymous browser id; the instructor's screen shows only how many have answered until the reveal.
- Speaker notes (`<aside class="notes">`) are taken out of the page in the same way. Only the course's instructor can fetch them, whatever the deck's status, and they are shown beside the page while teaching. **Present** on the teaching screen goes full screen with the page only, for when the instructor's screen is the one projected. **Projector window** opens the page alone in a second window for a separate display, leaving the notes on the first. Every window an instructor has open on a live class follows the others' page turns, so a phone can serve as a remote with the notes on it.
- The instructor's browser splits a deck into pages before uploading. Pages are shown in a sandboxed iframe, so deck scripts cannot touch the surrounding app.
- The app chrome uses the shared Social Shields stylesheet (`https://socialshields.org/brand.css`).

## Limits

25 MB per media file, 200 MB of media per deck, about 900 KB of HTML per page, one live class per course at a time. Storage is the Durable Objects allowance of the Cloudflare account.
