# TrueSight: Instructions for AI Coding Agents

Read this whole file before changing anything. It applies to every story. When this file and a story disagree, the
story's **Technical Notes** win for that story; everything else here still applies.

## What TrueSight Is

A web app for portfolio managers. They upload their holdings, and TrueSight reads each company's latest annual report
from the SEC, uses Gemini to find the suppliers and customers it names, checks every claim against the report's own
words, and shows the result as a graph. Each relationship links to the exact sentence in the report it came from.

## How You Will Be Given Work

The person you work with pastes a Jira story. It has four parts:

- **User Story**: who wants what, and why.
- **What We Agreed**: decisions already made. Follow them exactly.
- **Acceptance Criteria**: what must be true when you finish. Each one becomes at least one test.
- **Technical Notes**: addresses, data shapes and rules for building it. Follow them exactly.

Work on the branch named after the story's Jira key (e.g. `TS-28-Upload-a-Portfolio-CSV`). If the story is unclear,
or needs something that does not exist yet, **stop and ask**. Do not guess, and do not build a different story's work.

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
| `frontend/src/pages/` | One file per page |
| `frontend/src/components/` | Pieces used by more than one page, such as `Layout.tsx` |
| `frontend/src/api/client.ts` | `api()`, the only way the website calls the backend |
| `frontend/src/paths.ts`, `frontend/src/navigation.ts` | Every page's address, and the sidebar's links |
| `docs/examples/` | Real sample data to build and test against. Its README explains every file |

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

Until the Sign Up and Log In story is merged, `CurrentUser` is a placeholder that returns a demo user
(`demo@truesight.local`), and every endpoint is open. On a laptop the demo user also has one empty portfolio,
"Demo Portfolio" (usually id 1), so portfolio pages can be built before Manage My Portfolios exists. Once Sign Up and
Log In is merged, endpoints need a token: integration tests then log in using the test helper that story adds.

**Companies.** Get every company, whether a holding or a supplier the AI found, through
`CompanyDirectory.findOrCreate(name, cik, ticker, otherNames)`. Never create a `Company` directly. That way the same
company is one row whoever finds it first, and the Show Each Company Once story can improve the matching in one place.

**Database.** The tables already exist. In Sprint 1, **do not add migrations** and never edit `V1__sprint_1_tables.sql`:
a merged migration never runs again, so an edit silently does nothing on everybody else's database. Never change
`ddl-auto: validate`. Entities refer to each other by id (`portfolioId`), not by object links. If a story truly needs a
table change, stop and ask, so the team agrees one migration number.

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
- **Page addresses are fixed** in `src/paths.ts`. Use them exactly, and link with `paths.portfolio(id)` rather than
  typing addresses:

  | Address | Page | Built by |
  |---|---|---|
  | `/` | Home | Set Up the Project |
  | `/login`, `/signup` | Log In, Sign Up | Sign Up and Log In |
  | `/portfolios` | Portfolios: list, create, rename, delete | Manage My Portfolios |
  | `/portfolios/:portfolioId` | Portfolio: holdings, Upload CSV, Analyse | Upload a Portfolio CSV, then the annual report story adds Analyse |
  | `/portfolios/:portfolioId/supply-chain` | Supply Chain: the graph | See the Supply Chain as a Graph |

- **The open portfolio is the one in the address.** A page reads it with `const { portfolioId } = useParams()`.
  Nothing else stores which portfolio is open.
- **Adding a page:** create it in `src/pages/`, and add a `<Route>` in `App.tsx`. For the sidebar, add a line to
  `NAV_ITEMS` in `navigation.ts` for a page everyone sees, or to `PORTFOLIO_NAV_ITEMS` for a page belonging to the open
  portfolio. Only working pages appear in the sidebar.
- **Every page handles four states:** loading, error (show the `ApiError` message), empty (say why it is empty and what
  to do next), and loaded. `pages/HomePage.tsx` is the example.
- **Look:** use the colours and classes in `styles.css`. Dark theme, square corners, Space Grotesk. Labels, headings
  and buttons are in Title Case, e.g. "Upload CSV", "Show in Report".
- **No new libraries** unless the story names one (the graph story uses Cytoscape.js). No styling frameworks.
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
  demo user and Demo Portfolio in the real database.
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
| Show in Report | The link that opens the annual report on the SEC website at the evidence |
| Node, Edge | On the graph only: a company is a node, a relationship is an edge, pointing from supplier to customer |
| Status | Where a holding's analysis is: Waiting, Analysing, Done, Failed or No Report Found |

## Definition of Done

A story is done when all of these are true:

- The pull request title starts with the Jira key, and the pull request is merged.
- All tests pass.
- Every new backend address appears on the Swagger page.
- Data is still there after the backend restarts.
- The author can explain every file they changed.
