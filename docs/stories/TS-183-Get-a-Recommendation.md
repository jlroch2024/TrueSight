# TS-183 Get a Recommendation

| | |
|---|---|
| Type | Story |
| Status | To Do (in Jira on 23 October 2026) |
| Epic | [TS-6 News and Alerts](TS-6-News-and-Alerts.md) |
| Sprint | Sprint 3 |
| Points | 8 |
| Labels | s3-new, sprint-3 |
| Assignee | Allan Kok Song Yeong |

## User Story

As a portfolio manager, I want each alert to suggest what to check next, so that I can act on it quickly.

## What We Agreed

- **One suggested next step per event,** shared by every portfolio the event reaches, like the event's judgement. Gemini writes it when the event first produces an alert, from the headline, the AI's summary and reason, the company and its suppliers and customers on record.
- **Research support, never investment advice.** It names something to check or watch, in one or two sentences: never buy, sell, hold, a price target, or a change of allocation. An answer that breaks this is withheld automatically, with the note "Read as investment advice."
- **An analyst decides before managers see it.** The Analyst Desk's **Recommendations** tab lists Pending suggestions, oldest first, each with its event. The analyst **Approves** it, **Edits** it (and approves), or **Withholds** it with a note.
- **Managers see only approved ones,** on the alert card under the summary, headed "Suggested Next Step", with "Reviewed by an Analyst". Pending and withheld ones show nothing: no placeholder.

## Acceptance Criteria

1. A news check that creates an alert for a new event asks Gemini once for that event's suggestion; a second check, or a second portfolio reached by the same event, does not ask again.
1. A suggestion containing buy, sell, hold, price target, overweight or underweight (whole words, any capitals) is saved as Withheld with the note "Read as investment advice."
1. An analyst sees Pending suggestions on the Recommendations tab and can approve, edit and approve, or withhold with a note (a note is required to withhold).
1. A portfolio manager sees an approved suggestion on every alert for that event, the edited text when it was edited, and nothing for Pending or Withheld ones.
1. Portfolio managers get 403 from the Recommendations endpoints.
1. A failed Gemini call leaves the news check finished and the alert in place; the suggestion is asked for again at the next check.

## Technical Notes

- **When:** a new `NewsCheckStep`, `RecommendationStep`, `@Order(3)` (after Filter Out Noise and See What a News Event Affects). It asks for events that have an alert and no recommendation yet, one Gemini call per event, purpose `RECOMMEND`, with a response schema of `{ "nextStep": string }`.
- **Prompt:** a constant in `news/RecommendationWriter.java`. It states the rule above, asks for one or two sentences naming what a portfolio manager should check or watch, and gives the event's facts. Keep the forbidden-word check in code, not only in the prompt.
- **Endpoints (analysts only,** `currentUser.requireAnalyst()`**):** `GET /api/recommendations?status=PENDING` and `PUT /api/recommendations/{id}` with `{ "decision": "APPROVED" | "WITHHELD", "text": "…", "note": "…" }` (200, the updated item). `@Tag(name = "Recommendations")`.
- **Alerts:** `AlertResponse` gains `recommendation` (the approved text, or the edited text, or null).
- **Website:** the Recommendations tab in `AnalystDeskPage`; the alert card in `AlertsPage.tsx` shows the approved step.
- **Test data:** run the prompt once by hand on a real event from the live site (for example the ASML supply-shortage alert of 18 October 2026) and save Gemini's real answer in `docs/examples/` with the date, model and headline in the README.
- **Tests:** a `RecommendationStep` integration test with a fake `GeminiClient` serving the saved answer (asked once per event; withheld on a forbidden word); `RecommendationsIT` (the analyst flow and the 403); `AlertsIT` (only approved text reaches managers); website tests for the tab and the card.
