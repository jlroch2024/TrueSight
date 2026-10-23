# TS-191 AI Quality Dashboard

| | |
|---|---|
| Type | Story |
| Status | To Do (in Jira on 23 October 2026) |
| Epic | [TS-5 Trust and Review](TS-5-Trust-and-Review.md) |
| Sprint | Sprint 3 |
| Points | 5 |
| Labels | s3-new, sprint-3 |
| Assignee | Lim Zhe An |

## User Story

As an analyst, I want to see the AI's track record, so that we know how far to trust it and whether it is improving.

## What We Agreed

- The Analyst Desk's **Quality** tab, one section per AI step, each for the last 8 weeks, week by week:
   - **Finding relationships:** the share approved and rejected among those reviewed; how often a second-check doubt was later rejected (how well the second check spots bad ones).
   - **Judging news:** the share of decided articles the analyst overruled, split into noise made events and events made noise.
   - **Recommendations:** the share approved as written, edited, and withheld.
   - **Calls:** answered, unreadable and failed, and the median time taken.
- **Drawn without a chart library** (none is allowed): plain bars in HTML and CSS, with the numbers written beside them.
- Computed on request from the decisions already stored; nothing new is saved.

## Acceptance Criteria

1. Each section shows its figures for each of the last 8 weeks, from the stored decisions.
1. A week with no decisions shows "No Decisions" rather than 0%.
1. The figures match a hand count on a test database with known decisions.
1. Portfolio managers get 403.

## Technical Notes

- **Endpoint (analysts only):** `GET /api/quality` returning `{ relationships: [...], news: [...], recommendations: [...], calls: [...] }`, one entry per week. `@Tag(name = "AI Quality")`.
- **Website:** the Quality tab; bars as `div`s with a width in percent, `font-variant-numeric: tabular-nums` for the figures.
- **Tests:** `QualityIT` with known decisions across weeks (dates relative to now); website tests for the bars and the empty week.
