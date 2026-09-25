# TrueSight

Supply-chain risk for portfolio managers, from companies' own annual reports.

Upload a portfolio, and TrueSight reads each company's latest annual report from the SEC, uses AI to find the suppliers
and customers it names, checks every claim against the report's own words, and draws the result as a graph. Every
relationship links to the sentence in the report it came from.

## Getting It Running

Install these first: **Git**, **Java 21**, **Node.js** (the LTS version) and **Docker Desktop**. Docker Desktop must be
open whenever you run the database or the backend tests.

1. **Settings.** Copy `.env.example` to `.env` in this folder. Fill in only what your story needs.
2. **Database.** In this folder: `docker compose up -d`. Check it with `docker compose ps`: it should say `healthy`.
3. **Backend.** In `backend/`: `./mvnw spring-boot:run` (on Windows without Git Bash: `mvnw.cmd spring-boot:run`).
   It is ready when it says `Started TrueSightApplication`. Check http://localhost:8080/api/health.
4. **Website.** In `frontend/`, in a second terminal: `npm install`, then `npm run dev`, then open
   http://localhost:5173. The home page should say the backend is **Running**.

The Swagger page, listing every backend address, is at http://localhost:8080/swagger-ui.html.

## Running the Tests

- Backend: in `backend/`, `./mvnw verify`. This runs the quick tests, then the ones against a real database, so
  Docker must be running.
- Website: in `frontend/`, `npm test`.

Both run automatically on every pull request.

## What Each Piece Is For

| Piece | What it does | Why we need it |
|---|---|---|
| Spring Boot (Java 21) | The backend: receives requests and does the work | The course requires it |
| PostgreSQL | The database | Saves users, portfolios, reports and AI results |
| Flyway | Creates the database tables from numbered files | Every copy of the database is built the same way |
| Swagger | A web page listing every backend address, where each can be tried | The course requires documented endpoints |
| React | The website | A dashboard with pages and a live graph needs a proper frontend |
| Vite | Runs the website while developing, and builds it | Fast start-up, and one command to build |
| Testcontainers | Starts a real PostgreSQL in Docker for the tests | Tests check the real database, not an imitation |
| GitHub Actions | Runs every test on every pull request | Catches breakage before it is merged |
| Docker Compose | Starts the database with one command | Nobody installs PostgreSQL by hand |

## How We Work

Each Jira story gets one branch, named after its key (e.g. `TS-28-Upload-a-Portfolio-CSV`), and one pull request whose
title starts with the key. `AGENTS.md` has the rules every AI coding agent follows, and the Definition of Done.
