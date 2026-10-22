# TS-168 Show Dates and Labels the Same Way

| | |
|---|---|
| Type | Story |
| Status | Done (in Jira on 23 October 2026) |
| Epic | [TS-4 Supply Chain Map](TS-4-Supply-Chain-Map.md) |
| Sprint | Sprint 3 |
| Points | 3 |
| Labels | s2-fix, sprint-3 |
| Assignee | GOH ZYIK XYUN |

## User Story

As a portfolio manager, I want every date and label to read the same way on every page, so that TrueSight looks like one product and I never have to decode its wording.

## What We Agreed

- **One date style.** Dates read "Oct 18, 2026"; dates with a time read "Oct 18, 2026, 4:55 AM". The same on every page and in every browser language. Filing dates, which the API sends as "2026-02-24", read "Feb 24, 2026".
- **Review status labels in Title Case:** the graph legend's "Not Yet Reviewed"; the Inspector's "Approved by an Analyst on Oct 14, 2026", "Not Yet Reviewed by an Analyst" and "Rejected by an Analyst".
- **Every drop-down's arrow** points down when the list is closed and up when it is open.
- **Instructions read as sentences (item 56).** The empty Portfolio page's subtitle reads "Upload a CSV of your holdings to begin.", like its other instruction ("Press Analyse and TrueSight reads each company's latest annual report …"). Its status lines stay Title Case: "7 of 7 Holdings Analysed", "Reading Annual Reports: 5 of 14 Done".
- **What is supplied, as a label, in Title Case (item 60).** Under each supplier or customer in the Inspector: "Wafer Production", "Lithium-Ion Battery Cells", "Assembly, Testing and Packaging", "AMD GPUs". Inside a sentence it stays as written: the arrow's hover text and the Inspector's heading ("Panasonic supplies Tesla: lithium-ion battery cells"), and the Review card. The stored text never changes.
- Company names are not part of this story: Spell Each Company as Its Own Report Does covers them.

## Acceptance Criteria

1. A closed drop-down (the header's Portfolio list, and every other `Select`) shows its arrow pointing down; an open one shows it pointing up.
1. The holdings table's Filing Date, the Inspector's "Filed …" line and the alerts' article dates read like "Feb 24, 2026"; the holdings table's Last Analysed and the "News Checked …" line read like "Oct 17, 2026, 4:48 PM".
1. A filing date shows the same calendar day in every time zone: "2026-02-24" never reads "Feb 23, 2026".
1. The legend and the Inspector show the review status labels exactly as written above.
1. A portfolio with no holdings shows the subtitle "Upload a CSV of your holdings to begin."
1. Under each supplier or customer in the Inspector, what is supplied reads in Title Case as in the examples above, and capitals already in the text are kept.

## Technical Notes

- **Arrow (item 34).** In `frontend/src/styles.css`, the second `.select-chevron` rule (about line 536, `margin-top: 4px; transform: rotate(-135deg)`) lost its condition in an earlier fix, so it always applies. Its selector becomes `.select-button[aria-expanded='true'] .select-chevron`. Also remove the duplicated `.select-sizer` rule just above it. The first `.select-chevron` rule (about line 512) is the closed state and stays.
- **Dates (item 43).** In `frontend/src/format.ts`:
   - `formatDateTime` uses the fixed locale `'en-US'` instead of `undefined`, keeping `dateStyle: 'medium', timeStyle: 'short'`.
   - `formatDate` uses `'en-US'` with `dateStyle: 'medium'` (gives "Oct 18, 2026"). A date-only string matching `/^\d{4}-\d{2}-\d{2}$/` is read as a calendar date (`new Date(year, month - 1, day)`), never as UTC midnight, which shows the previous day west of Greenwich.
   - `formatAge`'s fallback past a week uses `formatDate`.
   - Use `formatDate` for filing dates: `PortfolioPage.tsx` line 203 (`h.reportDate`) and `Inspector.tsx` line 235 (`relationship.report.filingDate`). `ReviewPage.tsx` line 73 is changed by Easier Review, not here.
   - `AlertsPage.tsx` and the Inspector's review line already call `formatDate`, so they follow without other changes.
- **Labels (item 38).** `components/GraphLegend.tsx`: "Not yet reviewed" → "Not Yet Reviewed". `components/Inspector.tsx` lines 217–223: the three review lines as written above.
- **Subtitle (item 56).** `frontend/src/pages/portfolioSubtitle.ts` line 13, and its test line 14.
- **Supplied products (item 60).** Add `titleCase(text)` to `format.ts`: the first letter of each word, and of each part of a hyphenated word, becomes a capital; the small words a, an, and, as, at, by, for, in, of, on, or, the, to and with stay lower case unless they come first; no letter is ever made lower case, so "AMD GPUs" and "SoC" keep their capitals. Use it only at `Inspector.tsx` line 183 (`relationship.provides` in the supplier and customer lists), not in `relationshipSummary` (`supplyChain.ts`, line 354) or `ReviewPage.tsx` line 69, which are sentences.
- **Tests:**
   - `format.test.ts`: both formats; a date-only string keeps its day (test with `'2026-02-24'` → `'Feb 24, 2026'`); the existing `'14 October 2026'` expectation becomes `'Oct 14, 2026'`; `titleCase` with "wafer production", "lithium-ion battery cells", "assembly, testing and packaging" and "AMD GPUs" (real texts from the live site's relationships, 20 October 2026).
   - `GraphLegend.test.tsx` and the Inspector's tests: the new labels, and a supplied product in Title Case.
   - `portfolioSubtitle.test.ts`: the new empty subtitle.
   - A test for the arrow: render `Select`, check the closed button has `aria-expanded="false"` and that the open rule's selector in `styles.css` contains `[aria-expanded='true']` (read the file as text, as `noNativeControls.test.ts` reads sources).
