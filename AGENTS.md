# TrueSight: Instructions for AI Coding Agents

Read this before changing anything. It applies to every story.

## What TrueSight Is

A web app for portfolio managers. They upload their holdings, and TrueSight reads each company's annual report from the
SEC, uses Gemini to find the suppliers and customers it names, checks every claim against the report's own words, and
shows the result as a graph. Each relationship links to the exact sentence in the report it came from.

## How to Run It

Docker Desktop must be running.

| What | Command | Where |
|---|---|---|
| Database | `docker compose up -d` | repository root |
| Backend | `./mvnw spring-boot:run` (Windows without Git Bash: `mvnw.cmd spring-boot:run`) | `backend/` |
| Website | `npm install`, then `npm run dev`, then open http://localhost:5173 | `frontend/` |
| Backend tests | `./mvnw verify` | `backend/` |
| Website tests | `npm test` | `frontend/` |

The Swagger page is at http://localhost:8080/swagger-ui.html while the backend runs.

## Where Things Live

| Folder | What is in it |
|---|---|
| `backend/src/main/java/com/truesight/` | One package per feature: `user`, `portfolio`, `company`, `report`, `relationship`, plus `common` (errors), `config` and `health` |
| `backend/src/main/resources/db/migration/` | The database tables. `V1__sprint_1_tables.sql` creates every Sprint 1 table |
| `backend/src/test/java/com/truesight/` | Tests, in the same packages. `support/` has the shared test setup |
| `frontend/src/pages/` | One file per page |
| `frontend/src/api/client.ts` | `api()`, the only way the website calls the backend |
| `docs/examples/` | Real sample data to build and test against. See its README |

## Rules

1. **Do only the story you were given**, on its branch. If it needs something that is not there, stop and ask the person
   you are working with. Do not guess, and never copy code from other folders on this computer.
2. **Copy the existing patterns.** Endpoint: `health/HealthController.java`. Quick test: `HealthControllerTest.java`.
   Test against the real database: `HealthIT.java`. Page: `pages/HomePage.tsx`. Page test: `App.test.tsx`.
3. **Errors:** throw `ApiException` (e.g. `ApiException.notFound("Portfolio not found.")`). Never build an error
   response yourself. Every error reaches the website as `{"message": "..."}`.
4. **Who is logged in:** ask `CurrentUser.id()`. Every lookup of a portfolio includes the owner, e.g.
   `portfolios.findByIdAndUserId(id, currentUser.id())`. Another user's portfolio is "not found" (404), never 403.
5. **Database:** the tables already exist. Do not add migrations in Sprint 1, and never change `ddl-auto: validate`.
   Use the entity classes and repositories that are there; add repository methods if you need them.
6. **Answers:** endpoints return small records made for the purpose, never database entities.
7. **Every endpoint** has an `@Operation(summary = "...")`, so it is described on the Swagger page.
8. **Website:** call `api()`, never `fetch()`. To add a page: a `<Route>` in `App.tsx` and a line in `navigation.ts`.
   Labels and headings are in Title Case.
9. **Secrets** come from settings (`truesight.*` in `application.yml`, filled from `.env`). Never write a key in the code,
   and never commit `.env`.
10. **Tests:** class names end in `Test` (quick, no database) or `IT` (real database). Tests never call the SEC website
    or Gemini: use `docs/examples/` and saved responses instead.
11. **Commits and pull requests** start with the Jira key, e.g. `TS-28 Upload a Portfolio CSV`. Fill in the pull request
    template. Do not add "Co-Authored-By" or "Generated with" lines.
12. **Explain your work** to the person you are working with before they open the pull request. They must be able to
    explain every file they changed.

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
