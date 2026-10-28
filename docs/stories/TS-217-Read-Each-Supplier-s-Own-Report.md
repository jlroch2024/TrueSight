# TS-217 Read Each Supplier's Own Report

| | |
|---|---|
| Type | Story |
| Status | To Do (in Jira on 29 October 2026) |
| Epic | [TS-3 Reading Annual Reports](TS-3-Reading-Annual-Reports.md) |
| Sprint | Sprint 4 |
| Points | 8 |
| Labels | s4-new, sprint-4 |

## User Story
As a portfolio manager, I want TrueSight to read the annual report of every supplier of my holdings as well as my holdings' own, so that I can see who supplies my suppliers and hear about trouble one step further up the chain.

## What We Agreed
- **One level deeper, in full.** After a holding's report is read, TrueSight reads the latest annual report of **every supplier** it found, with no cap on how many. It does not go further: a supplier's suppliers are found, but their reports are not read.
- **Suppliers only.** A disruption flows down the chain to customers (as Two Steps Along the Graph decided), so a holding's customers are not read.
- **The same reading as a holding:** the supplier's ticker is looked up at the SEC, its latest 10-K or 20-F is downloaded, and Gemini finds its relationships, each checked against the report's own words.
- **Shared, like every report.** A supplier whose report is already stored, or who is a holding somewhere, is never downloaded or sent to Gemini again.
- **A supplier the SEC does not know** (no ticker, or a company that never files with the SEC, such as Samsung) is marked No Report Found and is not asked about again.
- **A holding is Done when its own report is read.** Its suppliers are read afterwards, in the background, and never hold up its status.
- **The graph shows the suppliers' suppliers,** as companies two steps from the holding. A supplier's other customers are not shown: they do not reach the portfolio.
- **The Holdings tab says how far it has got** while suppliers are being read: "Reading Suppliers' Reports, 12 of 30". Nothing is shown once all are read.
- Alerts, Check News and the Disruption Simulator already follow two steps along the graph; they use the new links with no change of their own.

## Acceptance Criteria
1. Analysing a holding whose report names three suppliers that file with the SEC also stores those three suppliers' reports and their relationships.
1. A supplier's own suppliers are found, but their reports are not read.
1. A holding's customers' reports are not read.
1. A supplier whose report is already stored is not downloaded or sent to Gemini again.
1. A supplier the SEC does not know is marked No Report Found and is not looked up again.
1. The holding is Done before its suppliers' reports are read, and a supplier that fails does not fail the holding.
1. The portfolio's graph shows a supplier's suppliers, linked to that supplier.
1. While suppliers are being read, the Holdings tab shows how many of them are read; once all are, it shows nothing.

## Technical Notes
- **Table:** `companies.report_status` and `companies.report_status_reason`, both in `V4__sprint_4_tables.sql` (Set Up Sprint 4). They use the holding status names (`HoldingStatus`). This story adds no migration.
- **Reading a report in one place:** move the steps of `AnalysisService.analyze` that find, download and read a company's report (from `sec.companyForTicker` to `relationshipFinder.findIn`) into a `report/ReportReader`, used for holdings and suppliers alike, so the two never differ.
- **`SupplierReader`** (in `report/`): when a holding becomes Done, it finds that company's suppliers (relationships in either direction where the other company supplies the holding; `NewsCheckService.supplierId` already works out the direction), skips any with a stored report or a `report_status`, sets the rest to `WAITING`, and reads them one by one on the analysis executor (`AnalysisJob`), recording `DONE`, `NO_REPORT_FOUND` or `FAILED` with its reason. Gemini calls use the `FIND_RELATIONSHIPS` purpose, as a holding's do.
- **SEC limit:** suppliers go through the same `SecClient`, so they keep to 10 requests a second with the holdings. Today `SecClient.request` is `synchronized` over the whole download (line 119), so one large 10-K holds up every other SEC request, and dozens of suppliers' reports would hold up a holding's own Analyse. Hold the lock only while waiting for the next request slot, then download outside it; `SecClientTest` shows two downloads overlapping while still starting at most 10 a second.
- **`GET /api/portfolios/\{portfolioId\}/relationships`** (`RelationshipService`, line 64) also returns the relationships in which one of the holdings' suppliers is the customer. **`GET /api/portfolios/\{portfolioId\}/holdings`** gains `suppliersRead` and `suppliersToRead`, counted from the holdings' suppliers' `report_status`.
- **Website:** `HoldingsTab.tsx` shows the line from criterion 8 under the holdings count; `view.ts` builds the new nodes from the relationships with no other change.
- **Tests:** `SupplierReaderIT` for criteria 1 to 6, with a fake `SecClient` serving `docs/examples/nvidia-10k.txt` and `amd-10k.txt` and a fake `GeminiClient` serving the saved `gemini-nvidia.json` and `gemini-amd.json`, counting the calls; `RelationshipsIT` for criterion 7; `HoldingsTab.test.tsx` for criterion 8.
