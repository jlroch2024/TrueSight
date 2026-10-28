# TS-95 Readable Supply Chain Graph

| | |
|---|---|
| Type | Story |
| Status | Done (in Jira on 29 October 2026) |
| Epic | [TS-4 Supply Chain Map](TS-4-Supply-Chain-Map.md) |
| Sprint | Sprint 2 |
| Points | 5 |
| Assignee | Lim Zhe An |

## User Story
As a portfolio manager, I want the graph to show each company clearly, with its logo and my position in it, so that I can read the supply chain at a glance.

## What We Agreed

- Companies are square tiles with their logo from Logo.dev, with a label underneath: a holding's ticker and weight ("NVDA 18%"), otherwise the ticker, or a short name.
- Holdings have a teal border.
- The Logo.dev key is not written in the code.
- Zooming and fitting the view stay as TS-89 made them.

## Acceptance Criteria

- Every company shows its logo (or a letter, when Logo.dev doesn't know the company) and its label.
- A label stays readable where an arrow passes behind it.
- Without a Logo.dev key, the graph still works, just without logos.

## Technical Notes

- A new `GET /api/settings` (logged-in only) returns `{ "logoToken": ... }` from `LOGO_DEV_TOKEN`, with tests `SettingsControllerTest` and `SettingsIT`. `.env.example` and docs/deployment.md list the setting.
- Logos come from `https://img.logo.dev/ticker/{ticker}`, or `/name/{name without its legal ending}`.
- `supplyChain.ts` gains `nodeLabel`, `shortName`, `plainName`, `formatWeight` and `logoUrl`, all tested.
- **Live site:** after merging, set `LOGO_DEV_TOKEN` on Cloud Run (the command is in docs/deployment.md).

---

h4.
