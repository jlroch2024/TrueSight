# TS-187 Two Steps Along the Graph

| | |
|---|---|
| Type | Story |
| Status | To Do (in Jira on 23 October 2026) |
| Epic | [TS-4 Supply Chain Map](TS-4-Supply-Chain-Map.md) |
| Sprint | Sprint 3 |
| Points | 3 |
| Labels | s3-new, sprint-3 |
| Assignee | Ben Aw Yong |

## User Story

As a portfolio manager, I want to hear about events at my suppliers' suppliers, so that hidden dependencies are not missed.

## What We Agreed

- An event at a **supplier's supplier** also reaches the holding: E supplies S, and S supplies the holding. Both links must not be rejected. Only that direction: a disruption flows down the chain to customers.
- Such a holding is marked **Indirect**, and its path reads "Carl Zeiss SMT supplies ASML Holding, which supplies TSMC".
- A holding reached directly is never also listed as indirect; between two indirect paths, Approved links are preferred, as for direct ones today.
- Indirect holdings count in the exposure like direct ones, and the card says how many are which: "1 Direct, 2 Indirect".
- Alerts already saved stay as they are; the next news check builds new ones with both steps.

## Acceptance Criteria

1. An event about E, where E supplies S and S supplies holding H, creates an alert reaching H, marked Indirect, with the two-link path.
1. A holding reached directly is listed once, as direct.
1. A rejected link on either step stops the indirect path.
1. A customer's customer is never reached.
1. The card shows the direct and indirect counts, and exposure includes both.

## Technical Notes

- `news/AlertBuilder.reached` (lines 105–120): after the direct pass, a second pass over the remaining holdings, finding E→S and S→H links (`joins` with the supplier direction checked), preferring Approved links (`PREFERRED`). Save the second link in `alert_holdings.second_relationship_id`.
- `AlertResponse.ReachedHolding` gains `indirect` and the middle company's name; the website builds the path with the names from Clearer Alerts.
- **Tests:** `AlertBuilderIT` for each criterion; `AlertsPage.test.tsx` for the path and counts.
