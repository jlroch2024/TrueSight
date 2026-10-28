# TS-216 One Workspace on a Phone

| | |
|---|---|
| Type | Story |
| Status | To Do (in Jira on 29 October 2026) |
| Epic | [TS-4 Supply Chain Map](TS-4-Supply-Chain-Map.md) |
| Sprint | Sprint 4 |
| Points | 5 |
| Labels | s4-new, sprint-4 |
| Assignee | Jeremy Roch |

## User Story
As a portfolio manager on my phone, I want the workspace to fit my screen one panel at a time, so that I can check my portfolio and its alerts away from my desk.

## What We Agreed
- **Designed with One Workspace** in the product owner's mock (27 and 28 October 2026) and shelved to Sprint 4 so the computer version could ship first.
- **Below 760px wide, one sheet at a time** over the graph, chosen from a tab bar at the bottom: Holdings, Graph, Alerts and Details. The open tab sits on a sliding plate; pressing it again returns to the graph. Details opens again on what was last chosen.
- **The sheets are the computer's panels** (the drawer's two tabs and the Inspector), not new screens, in the same look, with the same ticks.
- **The top bar** keeps the brand, the portfolio picker and the Open Alerts pill; the actions move into the sheets.
- **The graph's controls** fold for a small screen: search is a square icon until opened, the key opens from a Layers button, three holding colours to a row.

## Acceptance Criteria
1. On a 375px wide screen there is one sheet at a time with the tab bar, and nothing scrolls sideways.
1. Each tab opens its sheet; pressing the open tab again returns to the graph.
1. Choosing a company, relationship or alert in a sheet opens Details on it; Details opens again on the last choice.
1. Upload CSV, Check News and Analyse are reachable on a phone.
1. The graph's search and key work at 375px and stay inside the screen.

## Technical Notes
- **The mock is the design:** `frontend/.mock/src/Workspace.tsx` (the `compact` layout, the `dock` and `sheet`) and `app.css` on the product owner's machine.
- Reuse the workspace's components; the tab bar uses `useSlidingPlate`. The open sheet is page state, not part of the address.
- **Tests:** `WorkspacePage.test.tsx` below 760px for criteria 1 to 3; checked by hand at 375px.
