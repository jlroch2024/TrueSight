# TS-181 Alerts Are the One Source of News

| | |
|---|---|
| Type | Story |
| Status | To Do (in Jira on 23 October 2026) |
| Epic | [TS-6 News and Alerts](TS-6-News-and-Alerts.md) |
| Sprint | Sprint 3 |
| Points | 5 |
| Labels | s2-fix, sprint-3 |
| Assignee | isaiah |

## User Story

As a portfolio manager, I want news to reach me in one place, as alerts, and to see a company's alerts when I look at it on the graph, so that I never piece the same news together from two lists.

## What We Agreed

- **One source of news (item 66, decided).** The Supply Chain page's News tab is removed. It listed the 200 newest articles, noise included, and was built (PR #46) before the Alerts page existed; with noise hidden it would show nearly the same events as Alerts.
- **The graph reads the alerts (item 65).** The Inspector's "Recent News" read that list, never the alerts, and the list's 200-article cap hid older news: in the `test2@test.com` portfolio, all three ASML alerts are about articles from 16 to 18 October, while the list reached back only to 19 October, so ASML showed no news at all.
- A company in the Inspector shows **Alerts (N)** instead: the Open alerts about it and, for a holding, the Open alerts that reach it through a supplier or customer. Each shows its severity, headline, publisher, date and the path ("TSMC supplies Nvidia"), with Open Article. None: "No open alerts."
- **On the graph,** a company with Open alerts carries a small dot in its most severe alert's colour at its tile's corner.
- **The Portfolio page's News Checked line** counts Open alerts instead of articles: "News Checked Oct 20, 2026, 4:48 PM · 3 Open Alerts". If the alerts cannot be loaded, it ends "· Alerts Could Not Be Counted".
- **Check News** stays on the Portfolio and Alerts pages. The Supply Chain page has none: its only Check News was in the News tab.

## Acceptance Criteria

1. The Supply Chain page has no News tab.
1. Choosing a company in the Inspector lists its Open alerts, whatever their age, with the details above.
1. Choosing a holding also lists the Open alerts that reach it through a supplier or customer, with the path.
1. Companies with Open alerts carry the dot in their most severe alert's colour; a company whose alerts are all Dismissed carries none.
1. The Portfolio page's News Checked line shows the number of Open alerts.
1. Neither the Supply Chain page nor the Portfolio page asks for `GET /api/portfolios/{id}/news`.

## Technical Notes

- **Backend:** `AlertResponse.Article` gains `companyName` (the stored name of the company the article is about), filled in `AlertService` from the article's company. Graph companies are keyed by their stored names, so this is how the website finds an alert's company on the graph. `GET /api/portfolios/{id}/news` stays for analysts (TS-147).
- **Supply Chain page:** `frontend/src/pages/SupplyChainPage.tsx`. Remove the Holdings and News tabs (the Holdings panel stays alone), the news state and request (about lines 46–49 and 95–106), `newsCompany` and `selectCompanyByName`. Load `alertApi.list(portfolioId)` once, Open alerts only, and pass them to the Inspector and the graph. Delete `components/NewsPanel.tsx` and its test; `AssessmentTag` (in `NewsPanel.tsx`) goes too, once the Inspector no longer uses it.
- **Inspector:** `frontend/src/components/Inspector.tsx`, the company view's Recent News section (from about line 95). Replace it with Alerts: alerts whose `article.companyName` is the company's name, plus, for a holding, alerts whose `holdings` include its holding id (the Inspector already maps holdings to graph companies). Build the path from `supplierName` and `customerName`, as the Alerts page does after Clearer Alerts.
- **Graph:** `SupplyChainGraph.tsx` takes the most severe Open alert per company and draws the dot at the tile's corner (the same way the letter tiles are drawn as pictures), in the severity colours the Alerts page uses.
- **Portfolio page:** `PortfolioPage.tsx` lines 73–86 count Open alerts with `alertApi.list` instead of articles; `CheckNewsButton`'s `articleCount` becomes `openAlerts`, read as "· 3 Open Alerts" (Title Case: a status line).
- **Tests:** `SupplyChainPage.test.tsx` (no News tab; alerts passed on), the Inspector's tests (about the company, reaching the holding, none), `SupplyChainGraph.test.tsx` (the dot and its colour), `PortfolioPage.test.tsx` and `CheckNewsButton` (the count and its failure), `AlertsIT` (`companyName`).
