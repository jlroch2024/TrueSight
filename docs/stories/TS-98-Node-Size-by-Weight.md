# TS-98 Node Size by Weight

| | |
|---|---|
| Type | Story |
| Status | Done (in Jira on 29 October 2026) |
| Epic | [TS-4 Supply Chain Map](TS-4-Supply-Chain-Map.md) |
| Sprint | Sprint 2 |
| Points | 1 |
| Assignee | Jeremy Roch |

## User Story
As a portfolio manager, I want bigger positions drawn bigger, so that my largest exposures stand out.

## What We Agreed

- The tile size grows with the square root of the weight, so a big position is clearly bigger without swamping the rest.

## Acceptance Criteria

- A holding's tile grows with its weight, between 44 and 110 pixels.
- A holding without a weight is the usual size.

## Technical Notes

- `holdingSize(weight)` = 44 + 9 × √weight, capped at 110, so 1% is 53px, 10% is 72px, 20% is 84px and 40% is 101px.

---

h4.
