# TS-208 SEC Filing Timeline

| | |
|---|---|
| Type | Story |
| Status | To Do (in Jira on 29 October 2026) |
| Epic | [TS-3 Reading Annual Reports](TS-3-Reading-Annual-Reports.md) |
| Sprint | Sprint 4 |
| Points | 5 |
| Labels | s4-new, sprint-4 |

## User Story
As a portfolio manager, I want TrueSight to read my holdings' earlier annual reports, so that I can see how their suppliers and customers changed over the years.

## What We Agreed
- **Load History,** in One Workspace's top bar, reads each holding's two previous annual reports (10-K or 20-F) the same way as the latest: the same prompt and the same quote check. Before starting, it says what it will do: "Reads 20 Earlier Reports" (two per holding not yet read); while reading, "Reading Earlier Reports: 5 of 20".
- **Read once, kept.** A report already read is never read again. Holdings added later are offered on their own: "Load History for 2 New Holdings".
- **Years by filing date.** For each year, a holding counts the relationships of its latest annual report filed by the end of that year: what the reports said by then. Holdings file at different times (Apple in October, Nvidia in February), and this lines them up honestly. The years run from the earliest report read to the latest, then Today.
- **Each year's relationships are marked** New (not named the year before) or Kept, and the year lists those Gone (named the year before, not this year). A company is matched across years by TrueSight's company records, with the common names from Name Companies the Way People Know Them, so "OpenAI OpCo" one year and "OpenAI" the next are one company.
- **Older years never reach today's graph, alerts, review or simulator.** Their relationships go in `past_relationships` (see Set Up Sprint 4). Showing them on the graph is Scrub the Graph Through the Years.
- If the SEC has fewer than three annual reports for a holding, it has the years there are.

## Acceptance Criteria
1. Load History states how many reports it will read, then reads each holding's two previous annual reports once; a second Load History reads nothing new; a holding added later is offered on its own.
1. For each year, the history gives every holding's relationships from its latest report filed by the end of that year, each marked New or Kept, and the year's Gone list.
1. A company named differently across years is one company in the history.
1. Relationships from older reports never appear on today's graph, in alerts, in the review queue or in the simulator.
1. A holding with fewer annual reports has the years there are.
1. While reading, progress is shown; a failure shows the reason and Retry, and keeps what was already read.

## Technical Notes
- **SEC:** `report/SecClient.java`, `latestAnnualFiling` (line 76) reads the `filings.recent` list; add `annualFilings(cik, count)` returning up to `count` 10-K or 20-F filings, newest first. Keep the shared 10-a-second pacing and the `User-Agent`.
- **Reading:** a `HistoryService` downloads each older filing (stored in `reports` like any report), runs the relationship prompt (purpose `TIMELINE`) and `QuoteChecker`, and saves into `past_relationships`, with counterparties through `CompanyDirectory`. It runs in the background.
- **Endpoints:** `POST /api/portfolios/\{id\}/history` (202: reads every holding's missing years) and `GET /api/portfolios/\{id\}/history` (200): `\{ "status": "IDLE" | "READING" | "FAILED", "reason", "progress": \{ "done", "total" \}, "unread": n, "years": [\{ "year": 2025, "relationships": [\{ "key", "companyId", "counterpartyId", "type", "provides", "evidence", "reportUrl", "filingDate", "change": "NEW" | "KEPT" \}], "gone": ["key"…] \}] \}`. A relationship's `key` is the same in every year (company, counterparty and type), so the graph can follow it. Every lookup includes the owner (404 otherwise).
- **Keep older reports out:** `ReportRepository.findFirstByCompanyIdOrderByFilingDateDesc` stays the "latest report" everywhere else; nothing else reads `past_relationships`.
- **Website:** Load History (a `btn quiet`), its cost line and progress sit in the top bar's middle band, after the graph's title, where Scrub the Graph Through the Years later puts its scrubber: add a `barEnd` prop to `LivingGraph`, the counterpart of `barStart`. The right island's actions stay as they are. The Inspector's company view gains the company's history ("Named as a Supplier in 2024, 2025 and 2026") in `InspectorCompany.tsx`.
- **Test data:** an older real NVIDIA 10-K excerpt saved in `docs/examples/` beside `nvidia-10k.txt`, and Gemini's real answer for it, with README entries; a real SEC submissions response for the filing list.
- **Tests:** `SecClientTest` (the list), `HistoryIT` (each criterion, with a fake `SecClient` and `GeminiClient` serving the saved files), website tests for Load History.
