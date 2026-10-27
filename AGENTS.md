# TrueSight: Instructions for AI Coding Agents

Read this whole file before changing anything. It applies to every story. When this file and a story disagree, the
story's **Technical Notes** win for that story; everything else here still applies.

## What TrueSight Is

A web app for portfolio managers. They upload their holdings, and TrueSight reads each company's latest annual report
from the SEC, uses Gemini to find the suppliers and customers it names, checks every claim against the report's own
words, and shows the result as a graph. Each relationship links to the exact sentence in the report it came from.

## How You Will Be Given Work

The person you work with names a Jira story, or pastes it. Every story is also in `docs/stories/`, one file per Jira
issue named after its key (`TS-28-Upload-a-Portfolio-CSV.md`), with `README.md` listing them by sprint. Read the
story's file before you start, and the files of any story it names (its "Needs First" story, stories it builds on).
A story has four parts:

- **User Story**: who wants what, and why.
- **What We Agreed**: decisions already made. Follow them exactly.
- **Acceptance Criteria**: what must be true when you finish. Each one becomes at least one test.
- **Technical Notes**: addresses, data shapes and rules for building it. Follow them exactly.

Work on the branch named after the story's Jira key (e.g. `TS-28-Upload-a-Portfolio-CSV`). If the story is unclear,
or needs something that does not exist yet, **stop and ask**. Do not guess, and do not build a different story's work.

Jira is the source; the files are a copy. If a pasted story and its file disagree, the pasted one is newer: follow it,
and say so. Never edit a story file by hand: they are rebuilt from a Jira export (see `docs/stories/README.md`).

**Reviewing a pull request:** read the story file named by the pull request's key, then check the change against its
What We Agreed, every Acceptance Criterion, and its Technical Notes, as well as against this file.

## How to Run It

Docker Desktop must be running.

| What | Command | Where |
|---|---|---|
| Database | `docker compose up -d`, then `docker compose ps` should say `healthy` | repository root |
| Backend | `./mvnw spring-boot:run` (Windows without Git Bash: `mvnw.cmd spring-boot:run`) | `backend/` |
| Website | `npm install`, then `npm run dev`, then open http://localhost:5173 | `frontend/` |
| Backend tests | `./mvnw verify` (quick tests, then tests against a real database) | `backend/` |
| Website tests and build | `npm test`, then `npm run build` | `frontend/` |

The Swagger page is at http://localhost:8080/swagger-ui.html while the backend runs. Settings and keys go in `.env` in
the repository root: copy `.env.example`.

## Where Things Live

| Path | What is in it |
|---|---|
| `backend/src/main/java/com/truesight/company/CompanyDirectory.java` | The one way to find or create a company |
| `backend/src/main/java/com/truesight/` | One package per feature: `user`, `portfolio`, `company`, `report`, `relationship`. Plus `common` (errors), `config` (app-wide setup) and `health` (the example endpoint) |
| `backend/src/main/resources/application.yml` | Every setting, filled from `.env` |
| `backend/src/main/resources/db/migration/` | The database tables. `V1__sprint_1_tables.sql` creates every Sprint 1 table |
| `backend/src/test/java/com/truesight/` | Tests, in the same packages as the code. `support/` holds the shared test setup |
| `frontend/src/pages/` | One file per screen: the workspace (`WorkspacePage.tsx`), Log In and Sign Up (`AuthPage.tsx`), the Analyst Desk and its tools |
| `frontend/src/components/` | Pieces used by more than one screen, such as `Layout.tsx`, `Tile.tsx`, `Segmented.tsx` |
| `frontend/src/components/workspace/` | The workspace's slots: the top bar, the drawer's tabs and the Inspector |
| `frontend/src/api/client.ts` | `api()`, the only way the website calls the backend |
| `frontend/src/paths.ts`, `frontend/src/navigation.ts` | Every screen's address, and where screens are reached from |
| `docs/examples/` | Real sample data to build and test against. Its README explains every file |
| `docs/stories/` | Every Jira story, one file per issue, copied from a Jira export. Its README lists them by sprint |

## Backend Rules

**Structure.** A feature is built in three layers, each in the feature's package:

- `FooController`: receives the request, checks the input, calls the service, returns the answer. No business logic.
- `FooService`: the actual work. Put `@Transactional` on methods that change the database.
- `FooRepository`: database access. The Sprint 1 repositories already exist; add methods to them as needed.

Requests and answers are small `record`s made for the purpose, e.g. `CreatePortfolioRequest`, `PortfolioResponse`.
**Never return an entity class** from an endpoint.

**Addresses.** Everything is under `/api/`. Use plural nouns and nest what belongs to something:
`/api/portfolios`, `/api/portfolios/{id}/holdings`. Use the address exactly as the story's Technical Notes give it.

| Action | Method | Success status |
|---|---|---|
| Read | `GET` | 200 |
| Create | `POST` | 201, with the created thing as the answer |
| Change | `PUT` | 200 |
| Delete | `DELETE` | 204, no answer |
| Start background work | `POST` | 202 |

JSON field names are camelCase (`filingDate`). Dates are ISO text (`2026-02-25`).

**Errors.** Throw `ApiException`, and let `GlobalExceptionHandler` turn it into the response:

| Situation | Throw | Status |
|---|---|---|
| Bad input or a bad file | `ApiException.badRequest("...")` | 400 |
| Nobody logged in | `ApiException.unauthorized("...")` | 401 |
| Missing, or belongs to another user | `ApiException.notFound("...")` | 404 |
| Clashes with what exists, e.g. a name in use | `ApiException.conflict("...")` | 409 |

Never catch an exception just to build an error response. Messages are plain English sentences a user can read, ending
with a full stop, and never reveal internals such as table names. Every error reaches the website as
`{"message": "..."}`.

**Input checks.** Put rules on request records (`@NotBlank`, `@Size`, `@Email`) and `@Valid` on the controller
parameter. Broken rules become a 400 automatically.

**Who is logged in.** Ask `CurrentUser.id()`. Every lookup of a portfolio includes the owner:
`portfolios.findByIdAndUserId(id, currentUser.id())`, never `findById`. Another user's portfolio is "not found" (404),
never 403: saying "not allowed" would confirm it exists.

Every `/api/` address except `/api/health`, `/api/auth/signup` and `/api/auth/login` needs a login token.
`CurrentUser` reads the user from that token.

- **On a laptop,** log in as `demo@truesight.local` with the password `truesight-demo`. That account owns an empty
  portfolio, "Demo Portfolio" (usually id 1), so portfolio pages can be built before Manage My Portfolios exists. It is
  created only on laptops and in tests, never on the live site.
- **In integration tests,** log in with `TestLogins`:
  `mockMvc.perform(get("/api/portfolios").header("Authorization", logins.bearer("pm@example.com")))`. Use a different
  email in each test, so tests never see each other's data.
- **In quick `@WebMvcTest` tests,** add `@AutoConfigureMockMvc(addFilters = false)`: security is tested in `AuthIT`,
  not in every endpoint test.
- **On the Swagger page,** log in with `POST /api/auth/login`, then paste the token into **Authorize**.

**Companies.** Get every company, whether a holding or a supplier the AI found, through
`CompanyDirectory.findOrCreate(name, cik, ticker, otherNames)`. Never create a `Company` directly. That way the same
company is one row whoever finds it first, and the Show Each Company Once story can improve the matching in one place.

**Database.** Never edit `V1__sprint_1_tables.sql`: a merged migration never runs again, so an edit silently does
nothing on everybody else's database. Sprint 2 table changes are all in `V2__sprint_2_tables.sql`; no Sprint 2 story
adds another migration. Never change `ddl-auto: validate`. Entities refer to each other by id (`portfolioId`), not by
object links. If a later sprint truly needs another table change, stop and agree the migration number first.

**Relationships and reports are shared.** They come from public reports, so they belong to the report and the company,
never to a portfolio. A company already analysed is never downloaded or sent to the AI again.

**Settings and secrets.** Read settings with `@Value("${truesight.gemini.api-key}")` and similar, using the names in
`application.yml`. Never write a key, password or email address into the code, and never commit `.env`. Never log
passwords, tokens or keys.

**Outside services (SEC website, Gemini).** Put each behind one class, e.g. `SecClient`, so tests can replace it with a
fake. Use Spring's `RestClient`, with a timeout. Every SEC request sends the `User-Agent` from
`truesight.sec.user-agent`, and no more than 10 requests a second are made.

**Swagger.** Each controller has `@Tag(name = "...")`, and each endpoint `@Operation(summary = "...")`, written for a
person, e.g. "Upload a CSV into a portfolio".

**Copy these patterns:** endpoint `health/HealthController.java`; quick test `health/HealthControllerTest.java`; test
against the real database `health/HealthIT.java`; error test `common/GlobalExceptionHandlerTest.java`.

## Website Rules

- **Call the backend only through `api()`** in `src/api/client.ts`, never `fetch()`. It adds `/api`, the login token,
  and turns errors into an `ApiError` whose `message` is ready to show.
- **The website has no links.** Do not render anchors, `Link` or `NavLink`; use buttons for navigation and actions.
  Open external destinations with `openInNewTab()` from `src/format.ts`.
- **Addresses are fixed** in `src/paths.ts`. Use them exactly, and navigate with `paths.portfolio(id)` rather than
  typing addresses:

  | Address | Screen |
  |---|---|
  | `/` | Opens the first portfolio; a user with none goes to `/portfolios` (no Home page) |
  | `/login`, `/signup` | Log In and Sign Up: one card that switches between the two in place |
  | `/portfolios` | Opens the first portfolio, or asks a user with none to create one. There is no Portfolios page: the workspace's portfolio menu switches portfolios, and its Manage Portfolios opens a window to create, rename and delete them |
  | `/portfolios/:portfolioId` | The workspace: the whole portfolio on one screen (below). `?tab=alerts` opens its drawer on Alerts |
  | `/portfolios/:portfolioId/supply-chain`, `/portfolios/:portfolioId/alerts` | Old addresses, kept so saved links work: they open the workspace, the second on its Alerts tab |
  | `/review` | The Analyst Desk (Analysts only): a tile per analyst tool, the open tool in the address (`?tab=relationships`) |

- **A portfolio is one screen, the workspace** (`pages/WorkspacePage.tsx`). The living graph fills it, edge to edge,
  and glass panels float over it. **A story fills one of its slots; it never adds a page:**

  | Slot | What goes there | Where |
  |---|---|---|
  | Top bar, left | The brand, the portfolio menu (its name and analysis ring), the Open Alerts pill | `components/workspace/TopBar.tsx` (`PortfolioIsland`) |
  | Top bar, middle | The graph's own title (with how many companies and links) and search, centred between the islands. The Sprint 4 timeline (SEC Filing Timeline) decides how it shares this place | `LivingGraph`'s `topBar` |
  | Top bar, right | The portfolio's actions (Upload CSV, Check News, Analyse), Analyst Desk for analysts, the account | `TopBar.tsx` (`ActionsIsland`) |
  | Left drawer, Holdings tab | One row per holding, Add Holding, the shared suppliers and customers | `components/workspace/HoldingsTab.tsx` |
  | Left drawer, Alerts tab | The alerts, with Open, Closed and All | `components/workspace/AlertsTab.tsx` |
  | Inspector, right | Detail of what is chosen: a company, a relationship or an alert | `components/workspace/Inspector*.tsx` |
  | Bottom middle | Kept empty for the Sprint 4 chat (Ask TrueSight) | |

  Alerts go in the drawer's Alerts tab, detail in the Inspector, analyst tools in Analyst Desk tools. Nothing is drawn
  in a kept place until its story builds it: no placeholders or pretend controls. What the graph, the drawer and the
  Inspector show is worked out once, in `components/workspace/view.ts`.
- **The open portfolio and drawer tab are the ones in the address.** A screen reads the portfolio with
  `const { portfolioId } = useParams()`, and the tab from `?tab=`. Nothing else stores which portfolio or tab is open.
- **Adding a screen** is rare: the workspace and the Analyst Desk hold everything. A screen that is not a portfolio
  (such as Page Not Found) uses `PageFrame`; it gets a `<Route>` in `App.tsx`. There is no sidebar: `NAV_ITEMS` and
  `PORTFOLIO_NAV_ITEMS` in `navigation.ts` are empty.
- **Subtitles speak to the user.** A line under a tool's title says what the user can do there. It never describes the
  app to the team.
- **Every screen and slot handles four states:** loading (the drawer's loading rows), error (show the `ApiError`
  message, with Retry where trying again can help), empty and loaded. **An empty state is a short Title Case phrase with
  its action beside it** ("No Holdings Yet" with Upload CSV and Add Holding; "News Not Checked Yet" with Check News).
  `pages/WorkspacePage.tsx` is the example.
- **Look: "Signal and Schematic",** a circuit drawn on a night-blue sheet (the top of `styles.css` says it in full). Use
  the colours and classes in `styles.css`; do not invent new ones for something a class already does.
  - **Panels are frosted glass** with square corners and the same registration ticks on all four corners: every panel
    is `glass ticks` (the graph's own controls, `lg-glass lg-mark`). Buttons are `btn`, `btn primary` (teal glass, the
    one main action), `btn quiet` and `btn danger`; `icon-btn` for an icon alone.
  - **The chamfered corner belongs only to companies and their marks** (a holding's tile, an alert's beacon, an event's
    mark), with one even 1px edge. Panels, buttons and chips are square. Light is information: nothing glows for
    decoration. Icons have square caps.
  - **A company and its marks are drawn by the graph's own drawing wherever they appear:** `Tile` (a company's tile
    with its ring, beacon and shared badge), `FlowLink` (a link, as the graph draws its arrows) and `EventMark` (an
    event's kind, severity bars and confidence), all in `src/components/`. Never draw a company another way.
  - **Panels move on one curve** (`PANEL_MOVE` in `components/motion.ts`, 0.48s), the graph's view gliding with them.
    Tabs and filters are `Segmented`: every option the same width, the chosen one on a plate the browser slides
    (`useSlidingPlate`).
  - **One typeface, IBM Plex Sans, everywhere,** the graph included (`@fontsource-variable/ibm-plex-sans`). No
    monospace font and no small capitals. Figures that must line up use `font-variant-numeric: tabular-nums` (`.num`).
  - **Colours.** Teal is the app's controls; lime is what is supplied, what is new, and the analyst's marks; amber is
    only the shared badge. **One severity set everywhere** (beacons, cards, tags, disrupted links): High red, Medium
    orange, Low a quiet white (`--high`, `--medium`, `--low`, the graph's `ALERT_COLOUR`). **Holding colours,** three
    to a row in the graph's key: green, blue and violet, then cyan, magenta and yellow (`CHAIN_COLOURS`); the six
    largest holdings have one, the rest a grey ring.
  - **Title Case for every short phrase,** with no full stop: labels, buttons, headings, tags, counts, times, status
    lines and empty states ("10 of 10 Analysed", "3 Hours Ago", "No Open Alerts"). Full stops only on full sentences
    (explanations, error messages), which are in sentence case.
  - **Company names** are shown with the display name the API sends (`CompanyDisplayNames` in the backend); the website
    never reformats a name.
  - **Log In and Sign Up** are the same card, told apart by their accent: teal for Log In, lime for Sign Up. Their
    fields' labels and warnings are in capitals of the same font (`text-transform: uppercase`, 11 px): the one
    exception to Title Case.
  - **Phones:** the workspace keeps its computer layout below 760px until One Workspace on a Phone (Sprint 4).
- **No new libraries** unless the story names one. No styling frameworks.
- **No browser-default controls.** Use `Select`, `Checkbox` and `RadioGroup` from `src/components/`, never a native
  `<select>`, checkbox or radio button.
  Ask for confirmation with `useConfirm()` from `components/ConfirmDialog.tsx`, never `window.confirm`, `alert` or
  `prompt`, which some browsers never show.
- **Forms:** every input has a visible label. Disable the button while a request is running, so it cannot be sent
  twice.

## Tests

- Every Acceptance Criterion has at least one test. Name tests as sentences: `uploadingACsvWithNoTickerColumnIsRefused`.
- Backend class names end in `Test` (quick, no database, `@WebMvcTest`) or `IT` (whole app and a real PostgreSQL,
  `@IntegrationTest`). The ending must be exactly `IT`, or the test never runs.
- Website tests sit next to the file they test (`Foo.test.tsx`). Replace `fetch` with a fake, as `App.test.tsx` does.
- **Tests never call the SEC website or Gemini.** Use `docs/examples/`, saved responses and fake clients.
- **Never invent sample data.** Anything that looks like a real report, quote or AI answer must be real and say where it
  came from.
- **Saved samples must not expire.** When a saved sample meets a rule based on today's date (such as "the last 7
  days"), move the sample's dates relative to now in the test, keeping the gaps between them, as `NewsCheckIT` does.
  Otherwise the test passes on the day it is written and fails a week later.
- Never delete, skip or weaken a test to make it pass.

## Git and Pull Requests

1. Work only on the story's branch. Never commit to `main`.
2. Commit messages start with the Jira key: `TS-28 Read the CSV and find the ticker column`.
3. Before opening the pull request: run `./mvnw verify` and `npm test`, start the app, and check every Acceptance
   Criterion by hand.
4. Pull request title: `TS-28 Upload a Portfolio CSV`. Fill in every section of the template.
5. If GitHub says the branch is out of date or has conflicts, merge `main` into the branch, fix the conflicts, and run
   the tests again.
6. Stories can be built at any time but are **merged in order**: a story merges only after its "Needs First" story.
7. Do not add "Co-Authored-By" or "Generated with" lines to commits or pull requests.

## Things Never To Do

- Change files outside the story's scope, or reformat code you did not otherwise change.
- Change the shared pieces (`ApiException`, `GlobalExceptionHandler`, `CurrentUser`, `CompanyDirectory`, `api()`,
  `paths.ts`, `Layout.tsx`, the entities and `V1`) unless the story's Technical Notes say to.
- Run the live site without `SPRING_PROFILES_ACTIVE=prod`. Without it, the app runs in `local` mode and creates the
  demo account, with its publicly known password, in the real database.
- Copy code from any other folder on this computer. Build the story here, from this repository.
- Commit `.env`, keys, `node_modules`, `target` or `dist`.

## When You Finish

Explain to the person you are working with, file by file, what you changed and why. They must be able to explain every
file in the demo. Then they open the pull request.

## If Something Does Not Work

| Problem | Fix |
|---|---|
| Tests fail with "Could not find a valid Docker environment" | Start Docker Desktop and wait for "Engine running" |
| The backend cannot connect to the database | Run `docker compose up -d` in the repository root |
| "Port 5530 is already in use" | Something else uses that port. Set `DB_PORT` and `DATABASE_URL` in `.env`, as `docker-compose.yml` explains |
| The website says it cannot reach the server | Start the backend; the website expects it on port 8080 |
| A key or setting is missing | Copy `.env.example` to `.env` and fill it in |

## Terms

| Term | Meaning |
|---|---|
| Portfolio | A named group of holdings, belonging to one user |
| Holding | A company in a portfolio: its ticker and optional weight |
| Annual Report | A 10-K (US company) or 20-F (foreign company), from the SEC |
| Relationship | A supplier or customer connection stated in an annual report |
| Evidence | The report's own sentence that supports a relationship. Shown to users |
| Quote | What the AI gives as its source. Checked against the report, then replaced by the evidence. Never shown |
| Show Report | The button that opens the annual report on the SEC website without highlighting text |
| Show in Report | The evidence's button: opens the annual report on the SEC website, scrolled to the evidence and highlighted |
| Node, Edge | On the graph only: a company is a node, a relationship is an edge, pointing from supplier to customer |
| Status | Where a holding's analysis is: Waiting, Analysing, Done, Failed or No Report Found |
| Role | A user's access level: Portfolio Manager or Analyst. Authorization reads the current database value |
| Review Status | Whether a shared relationship is Unreviewed, Approved or Rejected |
| News Status | Whether a portfolio news check is Idle, Checking or Failed |
| News Assessment | The shared AI decision about whether an article describes a disruption event |
| Alert | A disruption event that reaches a portfolio, with the affected holdings recorded |
| Event Type | The kind of disruption reported, such as a supply shortage, labour action or cyber attack |
| Severity, Confidence | How serious an event is, and how sure the assessment is, each Low, Medium or High |
| Alert Status | Whether an alert is New, Acknowledged or Dismissed |

## Sprint 2 Shared Rules

- All Sprint 2 table changes belong in `V2__sprint_2_tables.sql`. No individual story adds another migration.
- Get News calls `NewsCheckStepRunner.run(portfolioId)` after saving new articles. It runs each `NewsCheckStep` in `@Order`; Filter Out Noise is order 1 and See What a News Event Affects is order 2.
- Use HTTP 403 only when the signed-in user's role cannot perform an action. A portfolio owned by somebody else remains 404.
- Analyst decisions apply to every user because relationships and news articles are shared data.
- User roles used for authorization are read from the database on every request; the token's role claim is for display only.

## Sprint 3 Shared Rules

- All Sprint 3 table changes are in `V3__sprint_3_tables.sql`. No individual story adds another migration.
- Every Gemini call goes through `GeminiClient.generate(AiRequest)` with an `AiPurpose`, so it is recorded in
  `ai_calls`. Never copy a report's text into the record: pass the prompt with the report replaced by
  `AiRequest.reportMarker(reportId, text)` as `recordedPrompt`. A caller that cannot read the answer calls
  `AiCallLog.unreadable(callId)`. Never log or record the API key.
- Analyst tools are tools of the Analyst Desk (`AnalystDeskPage.tsx`, at `/review`), never new pages. A story adds one
  entry to its `TOOLS`: a tile with its own colour and how much waits there. Every review queue decides with Approve and
  Reject.
- A portfolio manager's screen shows only what they act on. Analyst detail stays on the Analyst Desk.
- Recommendations are research support, never investment advice: no buy, sell or hold, and no price targets.

## Definition of Done

A story is done when all of these are true:

- The pull request title starts with the Jira key, and the pull request is merged.
- All tests pass.
- Every new backend address appears on the Swagger page.
- Data is still there after the backend restarts.
- The author can explain every file they changed.
