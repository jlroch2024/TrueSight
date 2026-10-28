# TS-141 Get Alerts

| | |
|---|---|
| Type | Story |
| Status | Done (in Jira on 29 October 2026) |
| Epic | [TS-6 News and Alerts](TS-6-News-and-Alerts.md) |
| Sprint | Sprint 2 |
| Points | 3 |
| Labels | sprint-2 |
| Assignee | Ben Aw Yong |

**Owner (suggested):** Ben · **Needs First:** See What a News Event Affects

## User Story

As a portfolio manager, I want an Alerts page that warns me about events affecting my holdings, with the reasons, so that I can decide what to do.

## What We Agreed

- An **Alerts** page for the open portfolio, in the sidebar after Supply Chain, with the Check News button at the top.
- Alerts are ordered by **severity**, then **exposure**, then newest.
- Each alert shows: the headline, its publisher and date, an **Open Article** button; the event type and severity; the AI's summary, reason and confidence, with "Based on the headline only"; and each holding reached, with its weight and why ("TSMC supplies NVIDIA"), marked "Not yet reviewed by an analyst" when that relationship is Unreviewed.
- The user can **Acknowledge** or **Dismiss** an alert. Dismissed alerts are hidden unless **Show Dismissed** is on, and can be restored.
- Under the alerts: "Checked 42 articles. 37 were not supply-chain events.", with a **Show Filtered News** button that lists them with the AI's reasons, so the user can see what was filtered out.

## Acceptance Criteria

1. After a news check finds an event reaching the portfolio, the Alerts page shows it with every field above.
1. A High alert is listed above a Low one, whatever their dates.
1. Acknowledging marks the alert Acknowledged; Dismissing hides it; both remain after refreshing.
1. Show Dismissed brings dismissed alerts back, and Restore makes one New again.
1. Show Filtered News lists the articles judged as noise, each with the AI's reason.
1. With no alerts, the page says "No events found in the last 7 days." after a check, or "News has not been checked yet." before one, and offers Check News.
1. Another user's alerts cannot be seen or changed (404).

## Technical Notes

- **Endpoints** (`AlertController`, `@Tag(name = "Alerts")`, owner checked with `findByIdAndUserId`):
   - `GET /api/portfolios/{id}/alerts?includeDismissed=false` → `AlertsResponse`.
   - `GET /api/portfolios/{id}/news?noise=true` → the filtered-out articles: `title`, `source`, `publishedAt`, `url`, `companyName`, `reason`.
   - `PUT /api/portfolios/{id}/alerts/{alertId}` with `{ "status": "ACKNOWLEDGED" | "DISMISSED" | "NEW" }` → 200 with the alert. Unknown alert, or another portfolio's: 404 "Alert not found."

```json
// AlertsResponse
{
  "articlesChecked": 42,
  "noiseCount": 37,
  "alerts": [{
    "id": 7, "status": "NEW", "exposure": 40.0, "createdAt": "2026-10-14T02:42:00Z",
    "article": { "title": "Fire halts production at TSMC plant", "source": "Reuters",
                 "publishedAt": "2026-10-13T08:00:00Z", "url": "https://news.google.com/rss/articles/..." },
    "assessment": { "eventType": "PRODUCTION_STOP", "severity": "HIGH", "confidence": "MEDIUM",
                    "summary": "A fire stopped production at one TSMC plant.", "reason": "..." },
    "holdings": [{ "holdingId": 3, "ticker": "NVDA", "weight": 40.0,
                   "link": "TSMC supplies NVIDIA", "reviewStatus": "UNREVIEWED" }]
  }]
}
```

(The values above illustrate the shape only; tests use the saved real sample data.)

- `articlesChecked` and `noiseCount` count the assessed articles of the companies in this portfolio's last check. `link` is built from the relationship as in See What a News Event Affects (`null` when the event is about the holding itself, shown as "The event is about this company"). Order is done in the query: severity HIGH, MEDIUM, LOW; then exposure; then `published_at` newest.
- **Website:** `src/pages/AlertsPage.tsx` at `paths.alerts`; add `{ label: 'Alerts', path: paths.alerts }` to `PORTFOLIO_NAV_ITEMS` after Supply Chain; `src/api/alerts.ts`. Reuse `CheckNewsButton`. Severity is a coloured tag (High, Medium, Low) using the theme's colours; event types are shown in words ("Production Stop"). Open Article uses `openInNewTab`. All four page states apply.
- **Tests:** `AlertsIT` for criteria 1–5 and 7; `AlertsPage.test.tsx` for the fields, the order, the buttons, Show Dismissed, Show Filtered News and both empty states.

**Out of scope:** recommendations (Sprint 3); e-mail or push notifications.
