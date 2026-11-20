# What Each Piece Is For

| Piece | What it does | Why we need it |
|---|---|---|
| Spring Boot (Java 21) | The backend: receives requests and does the work | The course requires it |
| PostgreSQL | The database | Saves users, portfolios, reports and AI results |
| Flyway | Creates the database tables from numbered files | Every copy of the database is built the same way |
| Swagger | A web page listing every backend address, where each can be tried | The course requires documented endpoints |
| SEC EDGAR | The SEC's public database of company filings | Where every annual report comes from |
| jsoup | Turns a report's web page into plain text | So the AI can read it, and its quotes can be checked |
| Google Gemini | Reads each report's business and risk sections, and lists the suppliers and customers with quotes | Relationships are written in free prose, differently in every report: rules cannot find them |
| SEC XBRL tags | The numbers a filing tags, such as how much of its revenue one customer brings | A dependency figure is read from the filing itself, never from an AI |
| Google News | Headlines about every company on a portfolio's chain | Where the news check finds disruptions, beside SEC 8-K filings |
| Logo.dev | Each company's logo, drawn on its tile | A company is known at a glance on a crowded graph |
| OpenPDF | Writes the Executive Briefing as a PDF | A briefing a portfolio manager can keep or send |
| React | The website | A workspace with a live graph needs a proper frontend |
| d3-force | The physics that lays out the graph, which is drawn on a canvas | Shared suppliers pull to the centre, where they stand out |
| Vite | Runs the website while developing, and builds it | Fast start-up, and one command to build |
| Vitest | Runs the website's tests | Fast tests beside the files they test |
| JaCoCo and Vitest coverage | Measure how much of the code the tests run | The build fails below 50% of lines on either side |
| Testcontainers | Starts a real PostgreSQL in Docker for the tests | Tests check the real database, not an imitation |
| GitHub Actions | Runs every test on every pull request, and deploys `main` | Catches breakage before it is merged, and keeps the live site up to date |
| Docker Compose | Starts the database with one command | Nobody installs PostgreSQL by hand |
| Docker image (`Dockerfile`) | Packages the website and backend together | The live site is one service with one address |
| Google Cloud Run | Runs the live site | No server to look after, and it costs almost nothing when idle |
| Google Cloud SQL | The live site's PostgreSQL | A managed database with backups |
| Google Cloud Scheduler | Calls the daily news check every morning | A sleeping Cloud Run service cannot keep its own timer |
| Google Secret Manager | Holds the live site's keys and passwords | Secrets never go in the repository |
