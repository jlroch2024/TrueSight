# TS-178 One Workspace

| | |
|---|---|
| Type | Story |
| Status | To Do (in Jira on 23 October 2026) |
| Epic | [TS-4 Supply Chain Map](TS-4-Supply-Chain-Map.md) |
| Sprint | Sprint 3 |
| Points | 8 |
| Labels | s3-new, sprint-3 |
| Assignee | Jeremy Roch |

## User Story

As a portfolio manager, I want my whole portfolio on one screen, with the supply chain graph at its centre, so that I can explore, analyse and act without moving between pages.

## What We Agreed

- The product owner's vision (17 October 2026): "everything on one page, with chat in the bottom center, timeline on top, and two drawers flanking". The Supply Chain page is already the start of it: Holdings and News on the left, the graph, the Inspector on the right.
- **One screen per portfolio,** at `/portfolios/:portfolioId`:

```
┌──────────────────────────────────────────────────────────────────────┐
│ [Portfolio ▾] Manage  (Timeline: Sprint 4)  Analyse  Upload CSV  News│
├────────────┬─────────────────────────────────────────┬───────────────┤
│ Holdings   │                                         │ Inspector     │
│ Alerts     │               the graph                 │ company,      │
│ (tabs)     │                                         │ relationship  │
│            │      (Ask TrueSight chat: Sprint 4)     │ or alert      │
└────────────┴─────────────────────────────────────────┴───────────────┘
```

- **What moves where:**

| **Today** | **In the workspace** |
|---|---|
| Portfolio page: the holdings table, Upload CSV, Analyse | The left drawer's Holdings tab (status, weight, report, Show Report), and Analyse and Upload CSV in the top bar |
| Supply Chain page | The workspace itself |
| Supply Chain's News tab | Already removed by Alerts Are the One Source of News: news reaches the workspace only as alerts |
| Alerts page | The left drawer's Alerts tab; choosing an alert shows it in full in the Inspector, and lights up the holdings it reaches on the graph |
| The sidebar's Portfolio, Supply Chain and Alerts | Gone: the drawer's tabs replace them |

- **Only what a portfolio manager acts on (round 4).** Every element earns its place: if a portfolio manager would not act on it, it is not on their screen. News reaches them only as alerts (items 66, 67).
- **Both drawers fold** (from A Flowing Supply Chain Graph), so the graph can take nearly the whole screen.
- **The timeline and the chat are places kept for Sprint 4** (SEC Filing Timeline, Ask TrueSight). Nothing is drawn there until those stories build them: no placeholder or pretend controls.
- **Stays separate:** Log In, Sign Up, and the Analyst's Review page (a different person doing a different job: working through a queue, not exploring a portfolio).
- **On a phone,** the drawers become full-screen tabs (Holdings, Graph, Alerts, Inspector).
- **Old addresses still work:** `/portfolios/:portfolioId/supply-chain` and `/portfolios/:portfolioId/alerts` open the workspace (the second with the Alerts tab open), so saved links and the team's habits do not break.

## Acceptance Criteria

1. Opening a portfolio shows the workspace: the top bar, the left drawer with Holdings and Alerts tabs, the graph and the Inspector.
1. Everything the Portfolio page does today works from the workspace: see each holding's status, weight, report and Show Report; Upload CSV; Analyse; Check News.
1. The Alerts tab lists the alerts as the Alerts page does today, with its Open, Dismissed and All filter and Dismiss; choosing one shows it in the Inspector and highlights the holdings it reaches.
1. The old Supply Chain and Alerts addresses open the workspace on the right tab.
1. The sidebar no longer lists portfolio pages; the Review page is unchanged for analysts.
1. On a 375 px wide screen, each drawer is a full-screen tab, and nothing scrolls sideways.
1. Every state still shows: loading, error, empty (a portfolio with no holdings says to upload a CSV), and loaded.

## Technical Notes

- **To design with the product owner before building:** the top bar's exact contents and order; how a chosen alert lights up the graph; whether the analyst's Review page also gets a link from the top bar.
- **Addresses:** `frontend/src/paths.ts` and `App.tsx`. `paths.portfolio(id)` becomes the workspace; `supplyChain` and `alerts` stay as addresses that open it on a tab (read the tab from the address, e.g. `?tab=alerts`, so the open portfolio and tab are still only in the address). `PORTFOLIO_NAV_ITEMS` in `navigation.ts` becomes empty. `paths.ts` and `Layout.tsx` are shared pieces: this story is allowed to change them.
- **Reuse, do not copy:** the holdings table from `PortfolioPage.tsx`, the alert card from `AlertsPage.tsx` and the workspace from `SupplyChainPage.tsx` become components the workspace places. Delete the old pages once nothing uses them, with their tests moved to the components.
- `AGENTS.md`**:** replace the page table with the workspace and its slots ("alerts go in the left drawer's Alerts tab, details in the Inspector"), so every later story fills a slot instead of adding a page. Sprint 3 and 4 stories that assume a page of their own (AI Audit Trail, AI Quality Dashboard, Disruption Simulator) are reviewed against it.
- **Order:** it merges after the fix stories that change these pages (Ask in TrueSight's Own Window, Clearer Alerts, Say When Something Fails) and after A Flowing Supply Chain Graph, which builds the folding drawers, so it moves their finished work instead of fighting it.
- **Tests:** the page tests become workspace tests: each criterion above, including the old addresses and the phone layout (checked by hand at 375 px; a test that the drawers become tabs below 768 px).
