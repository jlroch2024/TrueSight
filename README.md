<a href="https://jlroch2024.github.io/TrueSight/">
  <img src="docs/readme/hero.svg" width="100%" alt="TrueSight: Portfolio Supply-Chain Risk, Traced to the Source. Micron, Samsung and GlobalFoundries are drawn supplying NVIDIA, AMD and Qualcomm.">
</a>

<p align="center">
  <a href="https://jlroch2024.github.io/TrueSight/"><img src="docs/readme/button-live-site.svg" height="44" alt="Open Live Site"></a>
  &nbsp;
  <a href="https://truesight-1088051639519.asia-southeast1.run.app/swagger-ui.html"><img src="docs/readme/button-api-docs.svg" height="44" alt="API Docs"></a>
  &nbsp;
  <a href="https://jlroch2024.github.io/TrueSight/sprint-tracker/"><img src="docs/readme/button-sprint-tracker.svg" height="44" alt="Sprint Tracker"></a>
</p>

<p align="center">
  TrueSight shows portfolio managers which suppliers and customers their holdings depend on. Upload a CSV of your
  holdings, and TrueSight reads each company's annual report from the SEC, finds the suppliers and customers it names,
  and draws them as one graph, with each link showing the sentence it came from. Every day, it also checks the news and
  SEC filings, and warns you when an event affects your holdings.
</p>

<img src="docs/readme/demo.avif" width="100%" alt="The TrueSight workspace on a sample portfolio of ten chip and electronics companies: an alert that TSMC has suspended Chinese customers' access to advanced chipmaking, a simulated TSMC disruption reaching half the portfolio, the graph moved back to 2023, an answer from Ask TrueSight, and Panasonic's link to Tesla with its sentence from Tesla's 10-K, the important words highlighted.">

## How It Works

<img src="docs/readme/pipeline.svg" width="100%" alt="Two lines flow into your workspace. Reports: read the 10-K or 20-F, and each filing that names your holding; Gemini names who supplies and buys, each with a quote; the quote must be in the report and a second AI judges it, or it is never shown. News: Google News and SEC 8-Ks, each day, for your chain; Gemini splits real disruptions from the noise; TrueSight follows the links to the holdings an event reaches. The workspace shows each relationship with its sentence from the report, the important words highlighted, and an alert on each holding reached.">

## How We Check the AI

Every link shows the company's own sentence from its report, never the AI's words. This is the link at the end of the
video above:

> Currently, we rely on suppliers such as Panasonic and Contemporary Amperex Technology Co. Limited (CATL) for these cells.
>
> [Tesla 10-K, filed 29 January 2026](https://www.sec.gov/Archives/edgar/data/1318605/000162828026003952/tsla-20251231.htm)

TrueSight checks every quote against the report, and drops any quote that is not there. A second AI then reads the text
around the quote and judges whether it really describes a supplier or customer. A link it doubts raises no alerts. A
link is trusted more when both companies' reports name it, and an analyst can approve or reject any link, for every
user.

## What You Can Do

<table>
  <tr><td width="190"><strong>See the Chain</strong></td><td>A live graph of your holdings, their suppliers and customers, and their suppliers' suppliers. Companies that several holdings depend on are marked, and the graph grows as each report is read.</td></tr>
  <tr><td><strong>Check the Source</strong></td><td>Click a link to read its sentence from the report, with the companies and what is supplied highlighted. Show in Report opens the filing on the SEC website, scrolled to that sentence.</td></tr>
  <tr><td><strong>See How Much</strong></td><td>See how much of a company's revenue comes from one customer, taken from the filing's XBRL data and never from the AI. For example, Apple was 91% of Cirrus Logic's net sales in fiscal 2026.</td></tr>
  <tr><td><strong>Find Who Names You</strong></td><td>SEC full-text search finds other companies' reports that name your holding as a supplier or customer.</td></tr>
  <tr><td><strong>Get Warned</strong></td><td>Every morning, TrueSight checks Google News and SEC 8-K filings for every company in your supply chain. Each real disruption becomes one alert, showing how serious it is and how much of your portfolio it affects. Once an analyst approves it, the alert also suggests a next step.</td></tr>
  <tr><td><strong>Test a Failure</strong></td><td>Simulate Disruption shows which holdings would be hit if a supplier stopped, and what share of your portfolio that is.</td></tr>
  <tr><td><strong>Go Back in Time</strong></td><td>The SEC Filing Timeline redraws the graph from each year's reports, from 2023 to today, and shows which links are new and which have dropped off.</td></tr>
  <tr><td><strong>Ask TrueSight</strong></td><td>Ask about your portfolio in plain English. Answers use only TrueSight's own data, and show the links they are based on.</td></tr>
  <tr><td><strong>Take It With You</strong></td><td>Download Briefing makes a PDF of your holdings, open alerts, sole-source suppliers and biggest dependencies.</td></tr>
  <tr><td><strong>Correct the AI</strong></td><td>Analysts approve or reject relationships, news events and suggestions on the Analyst Desk, and their decisions apply to every user. They can also see every AI call, and how often analysts agree with each AI step.</td></tr>
</table>

## Measured on Real Filings

Before any change to an AI prompt is merged, we score it on real filings and headlines whose right answers are labelled.
The scores are saved in [`docs/examples/`](docs/examples/README.md).

| What Is Measured | Score |
|---|---|
| Links Found in the Part of a Report Gemini Reads (5 Reports) | 17 of 18 |
| Second AI Check: True Links Accepted, False Links Caught (60 Claims) | 23 of 23, 37 of 37 |
| Noise Filter: Real Disruptions Caught, False Alarms (80 Headlines, 3 Orders) | 91%, 1 of 174 |
| Headlines Read for a Busy Company in 24 Hours (Samsung, Tesla, NVIDIA: 17 to 19) | All of Them |

## Built With

Spring Boot · Java 21 · PostgreSQL · React · TypeScript · d3-force · Gemini · SEC EDGAR and XBRL · Google News · OpenPDF · Google Cloud Run

[Why Each Piece Is Here](docs/tech-stack.md) · [How the Live Site Is Deployed](docs/deployment.md) · [Rules for Contributors and AI Agents](AGENTS.md) · [Every Story](docs/stories/README.md)

<details>
<summary><strong>Run It Locally</strong></summary>

<br>

You need Git, Java 21, Node.js (LTS) and Docker Desktop, running. Copy `.env.example` to `.env` and fill in what you need.

```bash
docker compose up -d
```

```bash
cd backend && ./mvnw spring-boot:run
```

```bash
cd frontend && npm install && npm run dev
```

Open http://localhost:5173 and log in as `demo@truesight.local` with the password `truesight-demo`. This account exists only on laptops and in tests. The API docs are at http://localhost:8080/swagger-ui.html.

**Tests.** Run `./mvnw verify` in `backend/` (Docker must be running) and `npm test` in `frontend/`. They run on every pull request, and never call the SEC or Gemini. Instead, they use the real saved data in [`docs/examples/`](docs/examples/README.md).

</details>
