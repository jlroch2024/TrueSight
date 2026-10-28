# TS-178 One Workspace

| | |
|---|---|
| Type | Story |
| Status | Done (in Jira on 29 October 2026) |
| Epic | [TS-4 Supply Chain Map](TS-4-Supply-Chain-Map.md) |
| Sprint | Sprint 3 |
| Points | 8 |
| Labels | s3-new, sprint-3 |
| Assignee | Jeremy Roch |

## User Story
As a portfolio manager, I want my whole portfolio on one screen, with the supply chain graph at its centre, in one
clear look across the whole app, so that I can explore, analyse and act without moving between pages.

## What We Agreed

- **The product owner's vision** (17 October 2026): "everything on one page, with chat in the bottom center, timeline
on top, and two drawers flanking". Designed with the product owner as a clickable mock over 27 and 28 October 2026
(ten rounds of review); every decision below comes from it. This story builds the workspace for a computer screen
and restyles the whole app to match. **Phones are Sprint 4** (One Workspace on a Phone).
- **One screen per portfolio,** at `/portfolios/:portfolioId`. The living graph fills the whole screen, edge to edge,
and everything else floats over it:

```
┌──────────────────────────────────────────────────────────────────────────┐
│ [Brand] [Portfolio ▾ 10 of 10 Analysed] [3 Open Alerts]   (Timeline: S4) │
│                [Upload CSV] [Check News] [Analyse] [Analyst Desk] [Account] │
├────────────┐                                              ┌──────────────┤
│ Holdings   │                                              │ Inspector    │
│ Alerts     │              the living graph                │ company,     │
│ (tabs)     │                                              │ relationship │
│            │          (Ask TrueSight chat: Sprint 4)      │ or alert     │
└────────────┘                                              └──────────────┘
```

- **The top bar** is three floating islands. Left: the brand, the portfolio picker (its name, and under it a ring that
fills as holdings are analysed, with "10 of 10 Analysed"), and an Open Alerts pill in the most severe open alert's
colour that opens the Alerts tab. Middle: the graph’s own title and search, a place the Sprint 4 timeline will share. Right: Upload CSV, Check News,
Analyse, Analyst Desk (analysts only) and the account menu. Below 1440px wide the quieter actions fold to icons.
- **The left drawer** has two tabs, Holdings and Alerts.
   - **Holdings:** one row per holding: its tile (with its chain colour ring, alert beacon and shared badge), name,
ticker, supplier and customer counts, and weight bar; Add Holding, the weight edited in place, and Remove (from
Edit or Remove Holdings). Below them, "Shared Suppliers and Customers": companies several holdings depend on.
Choosing a row shows the company in the Inspector.
   - **Alerts:** "News Checked 3 Hours Ago", then Open, Closed and All. Each card shows its event mark, severity, type,
age, headline, the holdings it reaches and how much of the portfolio that is. Close Alert and Restore move it
between Open and Closed, with Undo. The stored status stays Dismissed; only the words change.
   - **It folds** into a narrow rail of the holdings' tiles.
- **The Inspector** shows what is chosen: a company (its report, Show Report, its suppliers and customers), a
relationship (the two companies with the link between them as the graph draws it, what is supplied on its own line,
the evidence and Show in Report) or an alert (what happened, why it matters, how it reaches each holding as a path
of linked tiles, the approved suggested next step, its sources). Closed, it leaves a tab at the right edge that opens
it again on what it last showed.
- **Choosing an alert spotlights it on the graph:** the rest dims; the event's company, the holdings it reaches and
the links between them stay lit, and the camera frames them, both links of a holding reached in two steps included.
- **The graph is the living graph,** drawn without a frame of its own, its controls kept clear of the panels: as a
panel opens or folds, the view glides by the same amount on the same curve. Its key reads "Shared Holding", "Disruption"
and "Open Alert".
- **Only what a portfolio manager acts on** is on their screen. News reaches them only as alerts.
- **The timeline and the chat are places kept for Sprint 4.** Nothing is drawn there yet.
- **Old addresses still work:** `/portfolios/:portfolioId/supply-chain` opens the workspace and
`/portfolios/:portfolioId/alerts` opens it on the Alerts tab.
- **One look across the app, "Signal and Schematic"** (a circuit drawn on a night-blue sheet):
   - Panels are frosted glass with square corners and the same registration ticks on all four corners (6px, 42%
slate, on the border), the graph's own controls included.
   - The chamfered corner belongs only to companies and their marks (a holding's tile, an alert's beacon, an event's
mark), with one even 1px edge. Light is information: nothing glows for decoration. Icons have square caps.
   - Panels move on one curve (0.48s); every sliding plate (toggles, tabs) is moved by the browser, so it never
stutters while a list draws. Toggle options are all the same width.
   - **One typeface, IBM Plex Sans,** for everything, tickers and figures included (tabular figures where they line
up). No monospace. It replaces Space Grotesk and Geist.
   - **Title Case for every short phrase,** no full stop; full stops only on full sentences. Empty states are short
phrases with their action beside them ("No Holdings Yet" with Upload CSV and Add Holding; "News Not Checked Yet"
with Check News).
   - **Holding colours,** three to a row in the key: green, blue and violet, then cyan, magenta and yellow, at the
strength of the app's own colours. The six largest holdings have one; the rest a grey ring.
   - **One severity set everywhere** (beacons, cards, tags, disrupted links): High red, Medium orange, Low a quiet
white. Amber is only the shared badge, teal the app's controls, lime what is supplied and the analyst's marks.
   - **The graph's marks are drawn once:** tiles, beacons, shared badges, disruption marks and links are drawn by the
graph's own drawing wherever they appear (lists, the Inspector, the Analyst Desk, the key).
- **Log In and Sign Up** become one glass card over a slowly drifting network, switching between the two in place.
- **The Analyst Desk** keeps its address and tabs and takes the look: a tile per tool with its own colour and what
waits there; the relationships queue grouped by the report each claim came from, each claim drawn as supplier, link
and customer with what is supplied below; a decided card slides towards its pile and the space closes. **Every queue**
**decides with Approve and Reject** (Confirm or Overrule an Event still stores Confirmed and Overruled). No keyboard
shortcuts.

## Acceptance Criteria

1. Opening a portfolio shows the workspace: the top bar, the left drawer with Holdings and Alerts, the graph filling
the screen and, once something is chosen, the Inspector.
1. Everything the Portfolio page does works from the workspace: each holding's status, weight, report and Show Report;
Add Holding, editing a weight and Remove; Upload CSV; Analyse; Check News.
1. The Alerts tab lists the alerts with Open, Closed and All; Close Alert and Restore work, with Undo.
1. Choosing an alert shows it in the Inspector and spotlights it on the graph, including both links of a holding it
reaches in two steps.
1. The drawer folds to its rail and opens again; the Inspector closes and its tab opens it again on the same thing.
1. The old Supply Chain and Alerts addresses open the workspace on the right tab.
1. The sidebar no longer lists portfolio pages; Analyst Desk appears in the top bar for analysts only.
1. Every state shows: loading, error, empty (a portfolio with no holdings offers Upload CSV and Add Holding) and loaded.
1. Every page uses IBM Plex Sans only; every glass panel has the same four ticks; only companies and their marks are
chamfered.
1. The graph, lists, cards and key use the same six holding colours in the agreed order and one severity set, and a
company's tile, beacon, badge and links look the same everywhere.
1. Log In and Sign Up are one card; the Analyst Desk shows its tools as tiles and offers Approve and Reject on every
queue.

## Technical Notes

- **The mock is the design:** `frontend/.mock/` on the product owner's machine (not committed): `src/app.css` holds
the look as CSS, `src/ui.tsx` the shared pieces, `Workspace.tsx`, `InspectorPanel.tsx`, `AnalystDesk.tsx` and
`Login.tsx` the screens. Build from it with real data through `api()`, never the mock's sample data.
- **Library named by this story:** `@fontsource/ibm-plex-sans` (weights 300 to 700), replacing
`@fontsource-variable/geist`.
- **Addresses:** `paths.portfolio(id)` is the workspace; `paths.supplyChain` and `paths.alerts` open it, with
`?tab=alerts` for the second, so the open portfolio and tab are only in the address. `PORTFOLIO_NAV_ITEMS` becomes
empty. This story may change `paths.ts`, `App.tsx`, `navigation.ts` and `Layout.tsx`.
- **Pieces:** `pages/WorkspacePage.tsx` places the islands, the drawer (`components/workspace/HoldingsTab.tsx`,
`AlertsTab.tsx`) and the Inspector (`components/workspace/Inspector*.tsx`, replacing `Inspector.tsx`). Shared
pieces in `components/`: `Tile.tsx`, `FlowLink.tsx`, `Segmented.tsx`, `EventMark.tsx`, `useSlidingPlate.ts`.
Delete `PortfolioPage.tsx`, `SupplyChainPage.tsx` and `AlertsPage.tsx` once nothing uses them, moving their tests.
- **Graph:** `LivingGraph` gains `focus` (the spotlight), `peekNodeId`, `bare` and `insets`; `render.ts` exports
`drawArrow`, `drawChevron`, `drawTileMark`, `drawBeaconFace`, `drawSharedBadge` and `drawRiskMark` for the shared
pieces. `CHAIN_COLOURS`, `ALERT_COLOUR` and `RISK_COLOUR` take the colours above.
- **Backend:** the alert response gains, for each holding reached in two steps, `secondRelationshipId` (the link from
the middle company to the holding), set where `AlertService` builds the reached holdings.
- `AGENTS.md`: the page table becomes the workspace and its slots (alerts in the drawer's Alerts tab, detail in
the Inspector, analyst tools as Analyst Desk tabs); the Look section is rewritten for IBM Plex Sans, the design
language, the colours, Title Case and the short empty states.
- **Phones:** until One Workspace on a Phone (Sprint 4), below 760px the workspace keeps the computer layout; the
mock's phone design is kept for that story.
- **Tests:** a test per criterion in `WorkspacePage.test.tsx` and the new components' tests; `AlertsIT` for
`secondRelationshipId`; `model.test.ts` keeps beacon colours equal to the severity tags; the Analyst Desk's tests
use Approve and Reject.
