# TS-207 Disruption Simulator

| | |
|---|---|
| Type | Story |
| Status | To Do (in Jira on 29 October 2026) |
| Epic | [TS-4 Supply Chain Map](TS-4-Supply-Chain-Map.md) |
| Sprint | Sprint 4 |
| Points | 5 |
| Labels | s4-new, sprint-4 |

## User Story
As a portfolio manager, I want to see what happens to my portfolio if a supplier stops, so that I can see which holdings it would hit before it happens.

## What We Agreed
- **A scenario is "this company stops supplying".** No period: TrueSight has no stock or lead-time data, so a duration could only change the result through invented numbers. Every figure shown comes from the graph and the weights.
- **Started from the Inspector:** choosing a company shows **What If It Stops?**. The result shows:
   - the holdings it reaches **downstream**: a holding it supplies, a holding supplied by a company it supplies (two steps, as in Two Steps Along the Graph), or the company itself when it is a holding. Each comes with its path. A holding that only **supplies** the company is not reached: it loses a customer, which is a different risk, and counting it would overstate the exposure;
   - the exposure: the total weight of those holdings, or "3 of 7 Holdings" without weights (as Clearer Alerts does);
   - every link on those paths that the report calls a sole or single source, with its sentence and Show in Report;
   - for each link, its review status and confidence.
- **On the graph,** the stopped company and every path from it to a reached holding are lit up; everything else fades, until the user closes the scenario.
- **Every assumption is written on the result:** "Assumes Carl Zeiss SMT stops supplying entirely. Uses relationships from the latest annual reports; rejected ones are left out. Weights from your CSV."
- A company that reaches none of the holdings says so: "No holding in this portfolio depends on Carl Zeiss SMT."

## Acceptance Criteria
1. Choosing What If It Stops? on a supplier lists every holding it reaches downstream, direct and two-step, each with its path; a holding that only supplies the company is not listed.
1. The exposure is the reached holdings' total weight, or "N of M Holdings" without weights.
1. Sole- and single-source links on those paths are flagged with their sentences.
1. The graph lights up the paths and fades the rest; closing the scenario restores the graph.
1. The assumptions line is shown with every result; a company reaching nothing says so.
1. Another user's portfolio answers 404.

## Technical Notes
- **One reach rule, two directions.** Move `AlertBuilder.reached` (the company itself, then one step, then two steps, preferring Approved links, skipping rejected ones) into one class, `news/Reach.java`, with a direction: alerts keep today's behaviour exactly (one step either way, since an event at a customer also hurts its supplier; two steps downstream), and the simulator uses downstream only (the company supplies the holding). `AlertBuilder`'s tests must pass unchanged.
- **Endpoint:** `GET /api/portfolios/\{id\}/what-if?companyId=…` (200) returning `\{ company, holdings: [\{ holdingId, ticker, weight, indirect, path: [relationshipId…] \}], exposure, weighted, holdingCount, criticalLinks: [...], assumptions \}`. `@Tag(name = "Disruption Simulator")`, `@Operation(summary = "Show which holdings a company stopping would reach")`. No table, nothing saved.
- **Website:** What If It Stops? sits with the company view's actions in `components/workspace/InspectorCompany.tsx` (for any company, not only holdings). The result is a fourth Inspector view: add `\{ kind: 'scenario', nodeId \}` to `Chosen` in `components/workspace/view.ts`, with Close returning to the company. The graph is lit exactly as a chosen alert lights it: pass the stopped company, the reached holdings and the paths' relationships as the `LivingGraph`'s `focus` (`GraphFocus`), which already dims the rest and frames what is lit. Holdings and links are drawn with `Tile` and `FlowLink`, as the alert view draws its paths.
- **Tests:** `ReachTest` (the rule, shared); `WhatIfIT` (each criterion, with weights and without); website tests for the panel and the highlight.
