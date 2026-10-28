# TS-184 Confirm or Overrule an Event

| | |
|---|---|
| Type | Story |
| Status | Done (in Jira on 29 October 2026) |
| Epic | [TS-5 Trust and Review](TS-5-Trust-and-Review.md) |
| Sprint | Sprint 3 |
| Points | 5 |
| Labels | s3-new, sprint-3 |
| Assignee | GOH ZYIK XYUN |

## User Story

As an analyst, I want to confirm or overrule the AI's judgement of a news article, so that alerts are right for everyone.

## What We Agreed

- The Analyst Desk's **Events** tab lists the articles judged in the last 7 days for every company TrueSight follows, newest first, with the filter "Events · Noise · Decided". Each shows the headline, publisher, date, company, the AI's judgement (event type, severity, confidence, or Noise) and its reason, with Open Article.
- The analyst can **Confirm** (agree), or **Overrule**: make noise an event (choosing its type and severity), make an event noise, or change an event's severity. Overruling needs a note.
- **A decision applies to everyone at once.** Making an event noise removes its alerts from every portfolio; making noise an event creates its alerts for every portfolio it reaches; a new severity re-orders their alerts.
- **The AI's answer is kept.** A decided article shows "AI Said: Noise" beside the analyst's decision, so the AI Quality Dashboard can count how often it was overruled.
- This is where the noise is visible now: portfolio managers no longer see it (Clearer Alerts removed Filtered News).

## Acceptance Criteria

1. The Events tab lists the last 7 days' judged articles with the AI's judgement and reason, filtered as above.
1. Overruling an event to noise, with a note, removes its alerts from every portfolio that had one.
1. Overruling noise to an event, with a type and severity, creates alerts in every portfolio the event reaches.
1. Changing an event's severity changes it on every alert for that event.
1. Confirming records the analyst and time and changes nothing else.
1. A decided article shows both the AI's answer and the decision; overruling without a note is refused with 400.
1. Portfolio managers get 403.

## Technical Notes

- **Endpoints (analysts only):** `GET /api/news/assessments?filter=EVENTS|NOISE|DECIDED` and `PUT /api/news/assessments/{id}` with `{ "decision": "CONFIRMED" | "OVERRULED", "isEvent": bool, "eventType": "…", "severity": "…", "note": "…" }` (200). `@Tag(name = "News Review")`.
- **Saving:** the first time an analyst changes an assessment, copy `is_event`, `event_type` and `severity` into the `ai_*` columns, then change the real ones. Everything that reads assessments (alerts, the noise filter's counts) then follows without change.
- **Alerts for everyone:** add `AlertBuilder.rebuildFor(articleId)`. Event to noise: delete that article's alerts. Noise to event: find every portfolio whose news covers the article's company (the reverse of `NewsCheckService.companiesFor`), and run the existing builder for that article in each.
- **Website:** the Events tab in `AnalystDeskPage`, with `Select` for the type and severity; the note form is the confirmation, so no extra confirmation window.
- **Tests:** `AssessmentReviewIT` with two portfolios sharing a supplier: each criterion, including alerts appearing and disappearing in both; website tests for the tab.
