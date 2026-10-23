# TS-196 Publish a Scenario

| | |
|---|---|
| Type | Story |
| Status | To Do (in Jira on 23 October 2026) |
| Epic | [TS-4 Supply Chain Map](TS-4-Supply-Chain-Map.md) |
| Sprint | Sprint 4 |
| Points | 3 |
| Labels | s4-new, sprint-4 |

## User Story

As an analyst, I want to publish a named scenario, so that every portfolio manager can run it on their own portfolio.

## What We Agreed

- An analyst publishes a scenario from the Analyst Desk's **Scenarios** tab: a name ("TSMC Stops Supplying"), the company, and an optional description of why it matters.
- **Every portfolio manager sees published scenarios** in a **Scenarios** list in the workspace, and runs one on the open portfolio with one click: it opens the Disruption Simulator's result for that company.
- **Any analyst can unpublish** a scenario; it leaves every manager's list, and the record of who published and unpublished it stays.
- A scenario is only a company and words: the result is always worked out from the portfolio it is run on.

## Acceptance Criteria

1. An analyst publishes a scenario with a name and company (description optional); a blank name is refused with 400.
1. Every portfolio manager sees it in their Scenarios list, and running it shows the simulator's result for their open portfolio.
1. An analyst unpublishes it, and it leaves every list.
1. Portfolio managers get 403 from publishing and unpublishing.

## Technical Notes

- **Endpoints:** `GET /api/scenarios` (everyone: published ones), `POST /api/scenarios` with `{ name, companyId, description }` (analysts, 201), `DELETE /api/scenarios/{id}` (analysts, 204: unpublishes, never deletes the row). `@Tag(name = "Scenarios")`.
- **Website:** the Scenarios tab on the Analyst Desk (company chosen with `Select`); the Scenarios list in the workspace, as One Workspace's design places it, running `GET /api/portfolios/{id}/what-if`.
- **Tests:** `ScenariosIT` for each criterion; website tests for both lists.
