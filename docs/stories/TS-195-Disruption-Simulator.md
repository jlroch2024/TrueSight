# TS-195 Disruption Simulator

| | |
|---|---|
| Type | Story |
| Status | To Do (in Jira on 23 October 2026) |
| Epic | [TS-4 Supply Chain Map](TS-4-Supply-Chain-Map.md) |
| Sprint | Sprint 4 |
| Points | 5 |
| Labels | s4-new, sprint-4 |

## User Story

As a portfolio manager, I want to see what happens to my portfolio if a supplier stops, so that I can see which holdings it would hit before it happens.

## What We Agreed

- **A scenario is "this company stops supplying".** No period: TrueSight has no stock or lead-time data, so a duration could only change the result through invented numbers. Every figure shown comes from the graph and the weights.
- **Started from the Inspector:** choosing a company shows **What If It Stops?**. The result shows:
   - the holdings it reaches, directly or two steps away (the same reach as alerts, from Two Steps Along the Graph), each with its path;
   - the exposure: the total weight of those holdings, or "3 of 7 Holdings" without weights (as Clearer Alerts does);
   - every link on those paths that the report calls a sole or single source, with its sentence and Show in Report;
   - for each link, its review status and confidence.
- **On the graph,** the stopped company and every path from it to a reached holding are lit up; everything else fades, until the user closes the scenario.
- **Every assumption is written on the result:** "Assumes Carl Zeiss SMT stops supplying entirely. Uses relationships from the latest annual reports; rejected ones are left out. Weights from your CSV."
- A company that reaches none of the holdings says so: "No holding in this portfolio depends on Carl Zeiss SMT."

## Acceptance Criteria

1. Choosing What If It Stops? on a supplier lists every holding it reaches, direct and two-step, each with its path.
1. The exposure is the reached holdings' total weight, or "N of M Holdings" without weights.
1. Sole- and single-source links on those paths are flagged with their sentences.
1. The graph lights up the paths and fades the rest; closing the scenario restores the graph.
1. The assumptions line is shown with every result; a company reaching nothing says so.
1. Another user's portfolio answers 404.

## Technical Notes

- **One reach rule.** Move the reach used by `AlertBuilder` (direct, then two steps, preferring Approved links, skipping rejected ones) into one class, `news/Reach.java`, used by both alerts and the simulator, so they can never disagree.
- **Endpoint:** `GET /api/portfolios/{id}/what-if?companyId=…` (200) returning `{ company, holdings: [{ holdingId, ticker, weight, indirect, path: [relationshipId…] }], exposure, weighted, holdingCount, criticalLinks: [...], assumptions }`. `@Tag(name = "Disruption Simulator")`, `@Operation(summary = "Show which holdings a company stopping would reach")`. No table, nothing saved.
- **Website:** the Inspector's What If It Stops? button and result panel; `SupplyChainGraph.tsx` highlights the paths (the same fade as hovering a company).
- **Tests:** `ReachTest` (the rule, shared); `WhatIfIT` (each criterion, with weights and without); website tests for the panel and the highlight.
