# TrueSight

Supplier Risk Intelligence for Portfolio Managers, from companies' own annual reports.

Upload a portfolio, and TrueSight reads each company's latest annual report from the SEC, uses AI to find the suppliers
and customers it names, checks every claim against the report's own words, and draws the result as a graph. Every
relationship links to the sentence in the report it came from.

## Live Site

**https://truesight-1088051639519.asia-southeast1.run.app** (the Swagger page is at
[/swagger-ui.html](https://truesight-1088051639519.asia-southeast1.run.app/swagger-ui.html))

Every merge into `main` updates it automatically, once the tests pass. [docs/deployment.md](docs/deployment.md) says
what runs where on Google Cloud, and every step used to set it up.

## Status

**Sprint 1 is complete.** A portfolio manager can go from signing up to seeing their portfolio's supply chain, live on
the web:

| Area | What works |
|---|---|
| Accounts | Sign up and log in with an email and password (stored scrambled with BCrypt; a login lasts 2 hours) |
| Portfolios | Several portfolios per user: create, rename, delete and switch between them. Nobody sees anyone else's |
| Holdings | Upload a CSV with a ticker or symbol column, and an optional weight; uploading again replaces the holdings after a confirmation |
| Annual reports | Analyse finds each company's latest annual report on the SEC website: a 10-K for US companies, a 20-F for foreign ones. It runs in the background, each holding shows its status, and a failed holding can be tried again. Each report is downloaded once and shared by every user |
| Suppliers and customers | Gemini reads each report's business and risk sections and names the suppliers and customers, each with a quote. A relationship is kept only if its quote is really in the report, and what is shown is the report's own sentence, never the AI's |
| One company, many names | The same company is recognised under the SEC's name, a report's name and a short name such as "TSMC", so it appears once |
| Supply chain graph | Holdings, suppliers and customers as a graph with a live physics layout. Clicking an arrow shows its evidence, and Show in Report opens the SEC filing at that sentence |
| Live site | One service on Google Cloud Run, with Cloud SQL and Secret Manager. Every pull request must pass the backend tests, the website tests and a Docker build; merging into `main` deploys automatically |

**Next (Sprint 2):** a refreshed interface, one Portfolio page with portfolios managed from the header, and a richer
graph: company logos, supply flowing along the arrows, and suppliers shared by several holdings highlighted.

**The end goal:** TrueSight as supplier risk intelligence. It will watch the news about every company on a portfolio's
supply chain, separate real signals from noise, work out which holdings an event affects, and alert the portfolio
manager, showing how confident it is, and letting them correct a relationship the AI got wrong.

**Known limits today:** relationships come only from each company's latest annual report, so nothing is tracked over
time yet; and a supplier or customer that does not file with the SEC is recognised by its name alone.

## Getting It Running

Install these first: **Git**, **Java 21**, **Node.js** (the LTS version) and **Docker Desktop**. Docker Desktop must be
open whenever you run the database or the backend tests.

1. **Settings.** Copy `.env.example` to `.env` in this folder. Fill in only what your story needs.
2. **Database.** In this folder: `docker compose up -d`. Check it with `docker compose ps`: it should say `healthy`.
3. **Backend.** In `backend/`: `./mvnw spring-boot:run` (on Windows without Git Bash: `mvnw.cmd spring-boot:run`).
   It is ready when it says `Started TrueSightApplication`. Check http://localhost:8080/api/health.
4. **Website.** In `frontend/`, in a second terminal: `npm install`, then `npm run dev`, then open
   http://localhost:5173. The home page should say the backend is **Running**. On a laptop you can log in as
   `demo@truesight.local` with the password `truesight-demo`, which owns a Demo Portfolio holding the 10 sample
   companies from `docs/examples/holdings.csv`.

The Swagger page, listing every backend address, is at http://localhost:8080/swagger-ui.html.

## Running the Tests

- Backend: in `backend/`, `./mvnw verify`. This runs the quick tests, then the ones against a real database, so
  Docker must be running.
- Website: in `frontend/`, `npm test`.

Both run automatically on every pull request, with a build of the live site's Docker image. Tests never call the SEC
website or Gemini: they use the real saved data in `docs/examples/`.

## What Each Piece Is For

| Piece | What it does | Why we need it |
|---|---|---|
| Spring Boot (Java 21) | The backend: receives requests and does the work | The course requires it |
| PostgreSQL | The database | Saves users, portfolios, reports and AI results |
| Flyway | Creates the database tables from numbered files | Every copy of the database is built the same way |
| Swagger | A web page listing every backend address, where each can be tried | The course requires documented endpoints |
| SEC EDGAR | The SEC's public database of company filings | Where every annual report comes from |
| jsoup | Turns a report's web page into plain text | So the AI can read it, and its quotes can be checked |
| Google Gemini | Reads each report's business and risk sections, and lists the suppliers and customers with quotes | Relationships are written in free prose, differently in every report: rules cannot find them |
| React | The website | A dashboard with pages and a live graph needs a proper frontend |
| Cytoscape.js | Draws the supply chain graph, with a physics layout | Shared suppliers pull to the centre, where they stand out |
| Vite | Runs the website while developing, and builds it | Fast start-up, and one command to build |
| Testcontainers | Starts a real PostgreSQL in Docker for the tests | Tests check the real database, not an imitation |
| GitHub Actions | Runs every test on every pull request, and deploys `main` | Catches breakage before it is merged, and keeps the live site up to date |
| Docker Compose | Starts the database with one command | Nobody installs PostgreSQL by hand |
| Docker image (`Dockerfile`) | Packages the website and backend together | The live site is one service with one address |
| Google Cloud Run | Runs the live site | No server to look after, and it costs almost nothing when idle |
| Google Cloud SQL | The live site's PostgreSQL | A managed database with backups |
| Google Secret Manager | Holds the live site's keys and passwords | Secrets never go in the repository |

## How We Work

We work in Scrum sprints, tracked in Jira. Each Jira story gets one branch, named after its key (e.g.
`TS-28-Upload-a-Portfolio-CSV`), and one pull request whose title starts with the key. `AGENTS.md` has the rules every
AI coding agent follows, and the Definition of Done.
