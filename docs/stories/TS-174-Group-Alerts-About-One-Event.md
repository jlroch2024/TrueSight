# TS-174 Group Alerts About One Event

| | |
|---|---|
| Type | Story |
| Status | Done (in Jira on 29 October 2026) |
| Epic | [TS-6 News and Alerts](TS-6-News-and-Alerts.md) |
| Sprint | Sprint 3 |
| Points | 5 |
| Labels | s3-new, sprint-3 |
| Assignee | Allan Kok Song Yeong |

## User Story

As a portfolio manager, I want one alert per event, however many publishers report it, so that the Alerts page shows what happened rather than how often it was written about.

## What We Agreed

- On 20 October the GameStop portfolio had 23 alert cards for about 15 events: TSMC's demand story three times (The Motley Fool, Yahoo Finance, The American Bazaar), Microsoft and Nscale twice, a Tesla crash twice, the UK regulator twice, and China's export list twice from the same publisher, rated Low and Medium confidence.
- **Alerts about one event become one card.** Two alerts are the same event when they are about the **same company**, have the **same event type**, and their articles were published **within 3 days** of each other.
- The card shows the most severe of them (then the newest) as its headline, and lists every source under "Also Reported By" with its publisher, date and Open Article.
- Dismiss and Restore act on the whole event: every alert in the group gets the status (Clearer Alerts removes Acknowledge).
- The grouping is done when the page is asked for alerts: the stored alerts do not change, and no table changes.

## Acceptance Criteria

1. Three alerts about TSMC, all Supply Shortage, published on the same day by three publishers, show as one card listing all three sources.
1. Two alerts about the same company and type, published 5 days apart, stay two cards.
1. Two alerts about one company with different event types stay two cards.
1. Dismissing a grouped card dismisses every alert in it, and the Dismissed filter shows it again as one card.
1. The card's headline, severity and confidence are those of its most severe alert.

## Technical Notes

- **Backend:** in `AlertService.list`, group the ordered rows into events (same article `companyId`, same `eventType`, publish times within 3 days of the group's first). `AlertsResponse.alerts` becomes a list of events: `{ alertIds: number[], status, exposure, headline (the article and assessment of the lead alert), sources: [{ title, source, publishedAt, url }], holdings }`, where `holdings` is the union of the group's reached holdings. `status` is the lead alert's.
- **New endpoint** for the group's status: `PUT /api/portfolios/{id}/alerts/status` with `{ alertIds, status }` (`NEW` or `DISMISSED`), answering 200 with the event. Each id must belong to the portfolio, or 404. `@Operation(summary = "Change the status of every alert about one event")`.
- **Website:** `AlertsPage.tsx` renders events; "Also Reported By" as a compact list under the summary.
- **Test data:** build the alerts in `AlertsIT` from the real headlines above (titles, publishers and dates as listed on 20 October 2026), moved relative to now as `NewsCheckIT` does, so they never expire.
