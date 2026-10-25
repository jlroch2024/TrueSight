# TS-172 Clearer Alerts

| | |
|---|---|
| Type | Story |
| Status | In Progress (in Jira on 23 October 2026) |
| Epic | [TS-6 News and Alerts](TS-6-News-and-Alerts.md) |
| Sprint | Sprint 3 |
| Points | 5 |
| Labels | s2-fix, sprint-3 |
| Assignee | GOH ZYIK XYUN |

## User Story

As a portfolio manager, I want each alert to state its reach correctly and plainly, and to see only what I have not dealt with, so that I can trust the numbers and work through the list.

## What We Agreed

- **Exposure is right without weights (item 41).** A portfolio with no weights shows the reach as "2 of 7 Holdings". "%" appears only for real weights. Today such a portfolio shows "2%" for two holdings.
- **Names read the app's way (item 42).** Holdings Reached builds its sentence from `displayName()`: "ASML Holding supplies TSMC", not "ASML HOLDING NV supplies TSMC".
- **Labels in Title Case (item 44):** "Weight Not Provided", "Named in the Article" (was "The event is about this company"), "Not Yet Reviewed by an Analyst", "Based on the Headline Only", and the event type "Legal or Regulatory" (lower-case "or").
- **No repeated "Weight not provided" (item 45).** When the portfolio has no weights, the weight is left out of Holdings Reached.
- **Open until Dismissed (item 64, decided).** Acknowledge is removed: nothing used it. An alert is Open until it is Dismissed, and Restore makes it Open again. Alerts already Acknowledged count as Open.
- **A filter instead of Show Dismissed (item 63).** "Open (21) · Dismissed (2) · All (23)" replaces the Show Dismissed checkbox, which added the dismissed alerts to everything instead of showing them.
- **Dismissed alerts look dismissed (item 49).** Under All, a dismissed card is dimmed and tagged "Dismissed" beside its severity. After Dismiss, a line "Alert Dismissed. Undo" stays for 8 seconds; Undo makes it Open again.
- **Only alerts on the page (item 67, decided).** Show Filtered News and the line "Checked 200 articles. 184 were not supply-chain events." are removed: a portfolio manager acts on alerts, not on what the AI set aside. The noise stays stored, for the analyst (TS-147 Confirm or Overrule an Event) and the AI Quality Dashboard (TS-154). The page holds the filter, the alerts and Check News. This replaces item 47's rewording of that line.

## Acceptance Criteria

1. In a portfolio without weights, an alert reaching two of seven holdings shows Exposure "2 of 7 Holdings", and no Holdings Reached row mentions a weight.
1. In a portfolio with weights, Exposure shows the total weight with "%" and each row shows its holding's weight, or "Weight Not Provided" for a holding without one.
1. A reached holding linked through a relationship shows "{supplier} supplies {customer}" with both names through `displayName()`; one named in the article shows "Named in the Article".
1. Every event type reads in Title Case with "or" in lower case; the other labels read as written above.
1. There is no Acknowledge button; an alert stored as Acknowledged shows as Open; `PUT /api/portfolios/{id}/alerts/{alertId}` with `ACKNOWLEDGED` is refused with 400.
1. The filter shows Open, Dismissed and All with their counts; Open hides dismissed alerts and Dismissed shows only them.
1. Under All, dismissed cards are dimmed and tagged; Undo after Dismiss makes the alert Open again.
1. The page shows no Filtered News and no counts line.

## Technical Notes

- **Backend, package** `com.truesight.news`**:**
   - `AlertsResponse` gains `boolean weighted` and `int holdingCount`. In `AlertService.list` (line 60): `weighted` is true when any holding of the portfolio has a weight (the same test as `AlertBuilder.exposure`, line 128: reuse it as a small shared method rather than copying it); `holdingCount` is the portfolio's number of holdings.
   - `AlertsResponse` drops `articlesChecked` and `noiseCount`; `AlertService.list` stops calling `news.counts`, and `NewsListService.counts` goes if nothing else uses it. `GET /api/portfolios/{id}/news` stays: analysts will need it for TS-147.
   - `AlertResponse.ReachedHolding` replaces `String link` with `String supplierName` and `String customerName` (both null when the event is about the holding itself). Fill them in `AlertService.reachedHolding` (lines 124–143) from the companies' stored names (`companyById`), instead of building the sentence. `shortestNames` and `companyNames` are then unused there: remove them if nothing else uses them.
   - **Statuses:** `AlertService.setStatus` accepts `NEW` and `DISMISSED` only; `ACKNOWLEDGED` throws `ApiException.badRequest("An alert is either open or dismissed.")`. `AlertStatus` keeps `ACKNOWLEDGED`, because stored rows may have it and the table's check allows it; responses report it as `NEW`, so the website never sees it. No table change.
   - The stored `exposure` and its ordering in `AlertRepository.findOrdered` are unchanged.
   - Tests: `AlertsIT` (the new fields, with and without weights; the names; Acknowledged refused and reported as New; no counts).
- **Website:**
   - `frontend/src/api/alerts.ts`: the type changes above; `AlertStatus` becomes `'NEW' | 'DISMISSED'`.
   - `frontend/src/pages/AlertsPage.tsx`: Exposure (line 49) as `${exposure}%` when `weighted`, otherwise `${exposure} of ${holdingCount} Holdings`; Holdings Reached (lines 60–70); `words()` (lines 17–23) is replaced by a label map like `SEVERITY_LABELS`, one entry per `EventType` (`PRODUCTION_STOP`, `SUPPLY_SHORTAGE`, `LABOUR_ACTION`, `NATURAL_DISASTER`, `TRADE_RESTRICTION`, `FINANCIAL_DISTRESS`, `LEGAL_OR_REGULATORY`, `CYBER_ATTACK`, `OTHER_DISRUPTION`), with confidence keeping its own map; "Open" and "Dismissed" for the status.
   - **Filter:** load once with `includeDismissed=true` and filter in the page; a `RadioGroup` (from `src/components`) with the counts in its labels replaces the `Checkbox` "Show Dismissed" (about line 208).
   - **Remove:** the Acknowledge button (lines 82–86), `FilteredNews` (lines 97–113), the counts line and the Show Filtered News button (about lines 233–245), and the `newsApi` import if nothing else on the page uses it.
   - **Dismissed look:** class `alert-card dismissed` in `styles.css` (near `.alert-card-heading`, about line 1722), `opacity` around 0.55, and a tag in the existing `news-tag` style.
   - **Undo:** keep the dismissed alert in state for 8 seconds; Undo calls `alertApi.setStatus(portfolioId, id, 'NEW')`.
- **Tests:** `AlertsPage.test.tsx` for every criterion, using the API's new shape.
