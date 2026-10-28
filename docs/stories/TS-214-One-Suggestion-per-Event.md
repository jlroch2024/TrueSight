# TS-214 One Suggestion per Event

| | |
|---|---|
| Type | Story |
| Status | To Do (in Jira on 29 October 2026) |
| Epic | [TS-6 News and Alerts](TS-6-News-and-Alerts.md) |
| Sprint | Sprint 4 |
| Points | 3 |
| Labels | s3-fix, sprint-4 |

## User Story
As a portfolio manager, I want every alert card to show the suggested next step an analyst approved for its event, so that a suggestion is never lost because the news about one event came from several articles.

## What We Agreed
- **Found on 27 October 2026,** reviewing what Sprint 3 merged. Get a Recommendation (TS-183) writes one suggestion per **article**, but Group Alerts About One Event (TS-174) shows several articles about one event as **one card**. Three articles about one TSMC shortage therefore cost three Gemini calls and put three Pending suggestions on the analyst's Recommendations tab. The card shows only the suggestion of its lead (most severe) article, so if the analyst approves another one, the portfolio manager sees nothing. This breaks TS-183's own criteria: "asks Gemini once for that event's suggestion" and "sees an approved suggestion on every alert for that event".
- **An event is what TS-174 says it is:** articles about the same company, with the same event type, published within 3 days of the event's first article. This rule lives in one place, used by both the alert cards and the suggestions, so the two can never disagree again.
- **One suggestion per event.** Once any article of an event has a suggestion (Pending, Approved or Withheld), no other article of that event gets one, and Gemini is not asked again.
- **A card shows its event's approved suggestion,** whichever of its articles that suggestion belongs to: the edited text when it was edited, and nothing while the suggestion is Pending or Withheld.
- **The Recommendations tab lists one Pending suggestion per event,** the oldest. Suggestions saved twice for one event before this fix are left in the table; the tab hides a Pending one whose event already has an older suggestion.
- **No table change:** suggestions stay keyed by article (`recommendations.article_id`); events are worked out from the articles and their assessments, as the alert cards already do.

## Acceptance Criteria
1. A news check in which three articles about one company and one event type, published within 3 days, create alerts asks Gemini for one suggestion, and saves one.
1. Two articles about the same company and type, published 5 days apart, are two events, and each gets its own suggestion.
1. When the approved suggestion belongs to an article that is not the card's lead, the card still shows it, with the edited text when it was edited.
1. A Pending or Withheld suggestion is never shown on a card, whichever article it belongs to.
1. With two Pending suggestions saved for one event (as before this fix), the Recommendations tab lists only the older.

## Technical Notes
- **The rule in one place:** move the grouping out of `AlertService.group` (with `EVENT_WINDOW`, 3 days, and `GroupKey`) into a small class in `news/`, for example `NewsEvents`, that groups articles with their assessments into events. `AlertService` and `RecommendationWriter` both use it, and `RecommendationService.pending()` uses it to keep the oldest Pending suggestion per event. Same behaviour as today for the cards: TS-174's tests must still pass unchanged.
- **`RecommendationWriter.run`:** for each alerted article, work out its event across all saved articles (suggestions are shared by every portfolio, like the articles), and skip it when any article of that event already has a suggestion. Replace the per-article lock with a lock per event, so two news checks running at once still write only one.
- **`AlertService.eventResponse`:** take the approved suggestion of any article in the event (`recommendationByArticle` already holds them all), not only the lead's.
- **Tests:** an integration test for criteria 1 and 2 with a fake `GeminiClient` serving the saved `docs/examples/gemini-recommendation.json`, counting the calls; `AlertsIT` for criteria 3 and 4; `RecommendationsIT` for criterion 5. Articles in tests come from the saved news samples, with their dates moved relative to now as `NewsCheckIT` does, so the 3-day window never expires.
