# TS-175 Sharper Noise Filter

| | |
|---|---|
| Type | Story |
| Status | Done (in Jira on 29 October 2026) |
| Epic | [TS-6 News and Alerts](TS-6-News-and-Alerts.md) |
| Sprint | Sprint 3 |
| Points | 3 |
| Labels | s3-new, sprint-3 |
| Assignee | Ben Aw Yong |

## User Story

As a portfolio manager, I want alerts only for events that could disrupt supply or deliveries, so that I am not warned about lawsuits, leadership changes and accidents that do not affect my supply chain.

## What We Agreed

- On 20 October several alerts were not supply-chain events: Dua Lipa's TV-box lawsuit against Samsung (which reached Nvidia through Samsung's memory supply), Apple's CEO change, Netlist's settlement with Micron, a Tesla driver's crash (twice), and Samsung Display's patent fight.
- The prompt states the test plainly: an event must be able to **stop or delay the named company making, buying, shipping or selling goods**. Leadership changes, lawsuits and settlements over money or patents, consumer accidents and investigations of one product incident, and regulator commentary are noise unless the headline says production, supply or sales are affected.
- **Measured, not guessed.** Two teammates label this walkthrough's 23 alert headlines by hand, before seeing Gemini's new answer, as `news-labels.csv` was made. The new prompt is run once against them, and the real answer is saved with the date and model. The story is done when Gemini agrees with the labels on more headlines than the current prompt does, and on every headline the old prompt let through as a clear false alarm listed above.

## Acceptance Criteria

1. The prompt in `NewsAssessor.PROMPT` states the rule and the noise list above.
1. `docs/examples/news-labels-alerts.csv` holds the 23 headlines with both teammates' labels, and its README entry says where they came from.
1. `docs/examples/gemini-news-assessment-sharper.json` holds Gemini's real answer to the new prompt for those headlines, with the date and model in the README.
1. A test reads the saved answer through `NewsAssessmentReader` and checks the agreement with the labels, and that each false alarm listed above is now noise.

## Technical Notes

- Prompt: `backend/src/main/java/com/truesight/news/NewsAssessor.java`, `PROMPT` (lines 34–52). Keep the JSON fields exactly as they are (`index`, `isEvent`, `eventType`, `severity`, `confidence`, `summary`, `reason`).
- The 23 headlines are on the live site's Alerts page for the GameStop portfolio (`/portfolios/12/alerts`, Show Dismissed on), and in the `news_article` table. Copy title, publisher and date exactly.
- Run the new prompt once by hand (a short test-scope runner or `curl` with your own key), never from the test suite. Never write or edit an AI answer by hand: `AGENTS.md` requires real samples that say where they came from.
- Follow the README's existing section for `gemini-news-assessment.json` as the model for the new entries.
