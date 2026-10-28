# TS-176 Spell Each Company as Its Own Report Does

| | |
|---|---|
| Type | Story |
| Status | Done (in Jira on 29 October 2026) |
| Epic | [TS-4 Supply Chain Map](TS-4-Supply-Chain-Map.md) |
| Sprint | Sprint 3 |
| Points | 8 |
| Labels | s2-fix, sprint-3 |
| Assignee | Lim Zhe An |

## User Story

As a portfolio manager, I want every company spelled the way the company spells itself, so that names look right and read the same on every page.

## What We Agreed

- The SEC's register writes most names in capitals ("NVIDIA CORP", "ELI LILLY & Co", "UNITEDHEALTH GROUP INC"). From capitals alone no rule can tell that NVIDIA stays in capitals while Lilly does not, so today's website guesses, and shows "Nvidia", "ELI LILLY &" and "Unitedhealth Group".
- **A company is spelled the way its own annual report spells it.** TrueSight already stores every holding's report (`reports.text`). NVIDIA's own 10-K writes "NVIDIA" 158 times.
- **No hand-made list of spellings.** The spelling comes from the report, or from the rules below.
- **The name rule lives in one place, the backend.** Every response that carries a company also carries its display name, and the website shows that; it never reformats a name itself.
- **The rule, word by word**, applied to the stored name:
   1. The SEC's registration mark after a slash goes ("QUALCOMM INC/DE" → "QUALCOMM INC").
   1. Legal endings are dropped from the end, one word at a time (Inc, Incorporated, Corp, Corporation, Co, Company, Ltd, Limited, plc, NV, SA, AG, SE, LLC, LP, Pte, PBC), then any trailing "&" and commas: "ELI LILLY & Co" → "ELI LILLY"; "Merck & Co., Inc." → "Merck". A name is never emptied: "Company" stays.
   1. A trailing "COM" joins the word before it as ".com": "AMAZON COM" → "AMAZON.com".
   1. A word that already has a lower-case letter is never changed ("SK Hynix", "Carl Zeiss SMT", "Apple").
   1. A word in capitals, when the company has a report, becomes its most frequent spelling in that report: whole-word, ignoring case, but not counting matches written all in lower case (web addresses such as nvidia.com). A word the report never writes stays for step 6.
   1. Any word still in capitals, when the whole stored name is in capitals (the SEC's style): words of five letters or more become Title Case ("MICROSOFT" → "Microsoft"); shorter ones stay, as they are usually initials ("ASML", "TSMC"). When the stored name has lower-case letters elsewhere, the source chose its capitals, so they stay ("NVIDIA Corporation", named that way in another company's report, reads "NVIDIA").

| **Stored name** | **Has a report** | **Shown today** | **Shown after** |
|---|---|---|---|
| `NVIDIA CORP` | Yes | Nvidia | NVIDIA |
| `NVIDIA Corporation` | No | NVIDIA | NVIDIA |
| `ELI LILLY & Co` | Yes | ELI LILLY & | Eli Lilly |
| `UNITEDHEALTH GROUP INC` | Yes | Unitedhealth Group | UnitedHealth Group |
| `Merck & Co., Inc.` | Yes | Merck & | Merck |
| `AMAZON COM INC` | Yes | Amazon.com | Amazon.com |
| `ASML HOLDING NV` | Yes | ASML Holding | ASML Holding |
| `Systems on Silicon Manufacturing Company Pte. Ltd.` | No | Systems on Silicon Manufacturing Company Pte. | Systems on Silicon Manufacturing |
| `Anthropic, PBC` | No | Anthropic, PBC | Anthropic |
| `Carl Zeiss SMT` | No | Carl Zeiss SMT | Carl Zeiss SMT |
| `MICROSOFT CORP` | No | Microsoft | Microsoft |

Rows with a report assume the report writes the name as the company does; NVIDIA's is checked against `docs/examples/nvidia-10k.txt`, the others are checked by hand on the live site.

## Acceptance Criteria

1. For each row of the table, the API's display name is the "Shown after" column.
1. A holding whose report writes "NVIDIA" is shown as "NVIDIA" on the Portfolio page, the Holdings panel, the graph, the Inspector, the news list, the Alerts page and the Review page.
1. A company is shown the same way on every page.
1. The website has no code that reformats a company name: every name shown comes from a display name in the API.
1. Working out a display name reads the company's report at most once while the backend runs, until a newer report for that company is saved.

## Technical Notes

- **New class** `backend/src/main/java/com/truesight/company/CompanyDisplayNames.java` (`@Component`), beside `CompanyDirectory`, which does not change. `String of(Company company)` applies the six steps. It reads the report with `ReportRepository.findFirstByCompanyIdOrderByFilingDateDesc(companyId)`, only when step 5 has a word in capitals to spell, and keeps each result in a `ConcurrentHashMap` keyed by company id and report id, so a newer report spells the name afresh. A company with no report needs no report text.
- **Responses that gain the display name** (the stored `name` stays, unchanged, beside it):
   - `RelationshipResponse.CompanySummary(id, name, ticker)` gains `displayName`. It is built in `RelationshipService.java` (line 96) and `NewsArticleResponse.java` (line 41). The Review page's items use the same shape, so they follow.
   - `HoldingResponse` gains `companyDisplayName` (null until the holding is matched to a company), filled in `HoldingService` beside `companyName`.
   - `AlertResponse.ReachedHolding`'s `supplierName` and `customerName` (added by Clearer Alerts) carry display names.
   - `AnalysisService.HoldingView` has no callers: leave it.
- **Website:**
   - `src/api/relationships.ts`: `CompanySummary` gains `displayName: string`. `src/api/portfolios.ts`: the holding type gains `companyDisplayName: string | null`. `GraphNode` in `src/pages/supplyChain.ts` gains `displayName`.
   - **Delete** `displayName()` from `src/pages/supplyChain.ts`, with its tests (they move to the backend). `plainName` stays: the graph uses it to ask for logos and to draw a company's letter, not to show its name.
   - Show the API's display name at every place that called `displayName()`: `HoldingsPanel.tsx` (lines 35, 85), `Inspector.tsx` (102, 181, 210, 214, 253), `NewsPanel.tsx` (44, 87) unless Alerts Are the One Source of News has removed it, the Inspector's Alerts section if it has, `SupplyChainGraph.tsx` (580, 593), `PortfolioPage.tsx` (194), `ReviewPage.tsx` (63, 67, 68), `AlertsPage.tsx` (Holdings Reached), and `relationshipSummary` in `supplyChain.ts`. Graph node ids stay built from the stored name.
- `AGENTS.md`, Website Rules, "Look": the line "**Company names** are shown with `displayName()` from `pages/supplyChain.ts`" becomes "**Company names** are shown with the display name the API sends (`CompanyDisplayNames` in the backend); the website never reformats a name."
- **Tests:**
   - `CompanyDisplayNamesTest` (quick, no database; a fake report lookup): every row of the table that has no report; `NVIDIA CORP` with `docs/examples/nvidia-10k.txt` as its report gives "NVIDIA"; a word the report never writes falls to step 6; lower-case matches are not counted (a report text whose only "nvidia" is in `nvidia.com`); the report is read once for two calls.
   - An integration test that `GET /api/portfolios/{id}/relationships` and `GET /api/portfolios/{id}/holdings` include the display names.
   - Website tests: the components above show `displayName` from the API, not the stored name.
   - No other report text may be invented for tests; if more is needed, save a real excerpt of a real report in `docs/examples/` with its source in the README, as `nvidia-10k.txt` is.
