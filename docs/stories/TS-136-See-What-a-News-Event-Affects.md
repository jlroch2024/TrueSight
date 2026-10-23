# TS-136 See What a News Event Affects

| | |
|---|---|
| Type | Story |
| Status | Done (in Jira on 23 October 2026) |
| Epic | [TS-6 News and Alerts](TS-6-News-and-Alerts.md) |
| Sprint | Sprint 2 |
| Points | 5 |
| Labels | sprint-2 |
| Assignee | GOH ZYIK XYUN |

**Owner (suggested):** Zyik or Jeremy · **Needs First:** Filter Out

Noise

## User Story
As a portfolio manager, I want to know which of my holdings an event affects, and how much of my portfolio that is, so that I can judge how serious it is for me.

## What We Agreed
- An event about a company reaches a holding when the company **is** the holding, **supplies** it, or **buys from** it: one step along the graph, using relationships that are **not Rejected**.
- **Exposure** is the total weight of the holdings an event reaches, so the same event matters more to a portfolio that holds more of the companies involved. A holding with no weight counts as 0; if no holding in the portfolio has a weight, exposure is the number of holdings reached.
- Each event becomes **at most one alert per portfolio**, listing every holding it reaches and the relationship that links each one.
- Noise never becomes an alert. Unreviewed relationships count (Get Alerts marks them).

## Acceptance Criteria
1. An event at TSMC creates an alert in a portfolio holding NVIDIA, where TSMC supplies NVIDIA, naming NVIDIA and that relationship.
1. The same event creates no alert in a portfolio with no link to TSMC.
1. Two portfolios holding NVIDIA at 40% and at 5% get exposures of 40 and 5 for the same event.
1. An event reaching two holdings creates one alert listing both, with their total weight.
1. An event about a held company itself creates an alert with that holding and no relationship.
1. A Rejected relationship never leads to an alert.
1. An article assessed as noise never creates an alert.
1. Checking the news again does not create the same alert twice.

## Technical Notes
- **`AlertBuilder implements NewsCheckStep`**, `@Order(2)`, package `news`. For the portfolio: its holdings with a `company_id`; every assessed event among the articles of the companies checked; for each event company `E`, the holdings reached:

| Relationship row | Meaning | Reaches holding H |
|---|---|---|
| `company = H, counterparty = E, type = SUPPLIER` | E supplies H | yes |
| `company = H, counterparty = E, type = CUSTOMER` | E buys from H | yes |
| `company = E, counterparty = H, type = SUPPLIER` | H supplies E | yes |
| `company = E, counterparty = H, type = CUSTOMER` | H buys from E | yes |
| (none) `H.company_id = E` | the event is about H itself | yes, `relationship_id` empty |

- Skip relationships with `review_status = 'REJECTED'`. If several relationships link the same holding, keep one (the Approved one first, then the lowest id).
- Save `Alert` (exposure as above, `status = NEW`) and its `AlertHolding` rows in one transaction; skip when an alert already exists for (portfolio, article) (`UNIQUE` in the table).
- Exposure uses `holdings.weight` as stored (12.5 means 12.5%).
- **Tests:** `AlertBuilderIT` building companies, holdings and relationships through the repositories (and articles with assessments directly), one test per criterion.

**Out of scope:** two steps along the graph (Sprint 3); alerts for news older than 7 days.
