# TS-8 Set Up the Project

| | |
|---|---|
| Type | Story |
| Status | Done (in Jira on 23 October 2026) |
| Epic | [TS-7 Running TrueSight](TS-7-Running-TrueSight.md) |
| Sprint | Sprint 1 |
| Points | 5 |
| Labels | sprint-1 |
| Assignee | Jeremy Roch |

## What This Is
Work the team needs before any story can start. No user sees it directly.

## Acceptance Criteria

- One GitHub repository has a `backend` folder (Spring Boot, Java 21) and a `frontend` folder (React).
- The README explains how to run both on a new computer.
- `docker compose up -d` starts a PostgreSQL database.
- The Swagger page opens at /swagger-ui.html.
- The backend and frontend tests run automatically on every pull request.
- The repository contains `AGENTS.md`, a pull request template, and `.env.example`.
- Every endpoint returns errors in the same shape, through one shared error handler.
- One migration creates every table Sprint 1 needs.
- The frontend has page routing, the sidebar layout, and one helper for calling the backend.
- A placeholder current user lets stories that need a logged-in user work before sign-up is finished.
- One teammate has taken one story through the whole workflow, and any gaps found are fixed.

## Technical Notes

- The rule for this task: build only what two or more stories need at the same time. Anything one story uses belongs to that story.
- One example endpoint, `GET /api/health`, with one unit test and one test against a real database (Testcontainers). Later stories copy these patterns.
- Errors from every endpoint are a JSON object with a `message` field, produced by one shared error handler, so the frontend shows them the same way everywhere.
- Tables, in one Flyway migration: users, portfolios, holdings, companies, company names, reports and relationships. Hibernate only checks them (`ddl-auto: validate`). Stories do not add migrations of their own in Sprint 1.
- Frontend shell: React Router, a layout with the sidebar, and one `api()` helper that adds the sign-in token and turns errors into messages. Each story adds its own page and sidebar entry.
- Placeholder current user: one `CurrentUser` helper that returns a fixed demo user. The sign-up story replaces it with the real logged-in user.
- The demo user is created only when running locally and in tests, never on the live site.
- `.env.example` lists `DATABASE_URL`, `JWT_SECRET`, `GEMINI_API_KEY`, `GEMINI_MODEL` (`gemini-3.8-flash`) and `SEC_USER_AGENT` (`TrueSight contact@truesight.com`). The real `.env` is never committed.
- `docs/examples/` holds shared sample data: `holdings.csv` (the 10 sample companies), `relationships.json` (sample relationships in the shape `GET /api/portfolios/{id}/relationships` returns), `nvidia-10k.txt` (the plain text of NVIDIA's latest 10-K) and `gemini-nvidia.json` (a saved real Gemini answer for that report).
- The sample companies: NVDA, AMD, QCOM, TSLA, AVGO, LRCX, CRUS, ASML, STM and SONY. Each one's latest annual report names real suppliers or customers.
- `AGENTS.md` covers what TrueSight is, how to run it, where things live, the branch and pull request rules, the agreed terms, and the Definition of Done.
- Definition of Done: the pull request title starts with the Jira key and it is merged; all tests pass; new endpoints appear on Swagger; data survives a restart; the author can explain every file changed.
