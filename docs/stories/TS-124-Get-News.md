# TS-124 Get News

| | |
|---|---|
| Type | Story |
| Status | Done (in Jira on 29 October 2026) |
| Epic | [TS-6 News and Alerts](TS-6-News-and-Alerts.md) |
| Sprint | Sprint 2 |
| Points | 5 |
| Labels | sprint-2 |
| Assignee | Ben Aw Yong |

**Owner (suggested):** Allan · **Needs First:** Set Up Sprint 2

## User Story
As a portfolio manager, I want TrueSight to collect recent news about every company in my portfolio's supply chain, so that I hear about events at my suppliers and customers, not only at the companies I hold.

## What We Agreed
- A **Check News** button on the portfolio. It answers at once and works in the background, like Analyse.
- **Companies checked:** the portfolio's analysed holdings, plus every company linked to them by a relationship that is not Rejected (suppliers and customers).
- Up to **10 newest articles from the last 7 days** per company, in English.
- Articles are shared, like reports: an article already saved for a company is not saved again, and **a company checked in the last hour is not asked about again**.
- The portfolio shows "Checking news…" while it runs, then "News checked 14 October 2026, 10:42". If Google News cannot be reached, it shows why, and Check News can be pressed again.
- When the articles are saved, the next news steps run (Filter Out Noise, then See What a News Event Affects).

## Acceptance Criteria
1. Pressing Check News on an analysed portfolio saves recent articles for its holdings and their suppliers and customers, and the portfolio then shows when news was last checked.
1. Pressing it again within an hour does not ask Google News again about the same companies.
1. The same article is never saved twice for the same company.
1. Only articles from the last 7 days are saved, at most 10 per company.
1. Pressing Check News while a check is running is refused with "News is already being checked for this portfolio."
1. A portfolio with no analysed holdings is refused with "Analyse the portfolio before checking the news."
1. If Google News cannot be reached, the portfolio shows "Google News could not be reached. Please try again later.", and pressing Check News again works.
1. Another user's portfolio cannot be checked (404).
1. Tests never call Google News.

## Technical Notes
- **Endpoint:** `POST /api/portfolios/\{id\}/news-check` (controller `NewsController`, `@Tag(name = "News")`), answers **202**. Refusals are 409 with the messages above (criteria 5 and 6).
- **`PortfolioResponse`** gains `newsStatus` (`IDLE`, `CHECKING`, `FAILED`), `newsStatusReason` and `newsCheckedAt` (ISO time).
- **`NewsClient`** (`@Component`, the only class that calls Google News; replaced by `@MockitoBean` in tests):
   - `List<NewsItem> search(String companyName)`, where `record NewsItem(String title, String url, String source, Instant publishedAt)`.
   - URL: `https://news.google.com/rss/search?q="\{name\}"+when:7d&hl=en-US&gl=US&ceid=US:en` (URL-encode the name). Settings: `truesight.news.base-url` (default `https://news.google.com/rss/search`) and `truesight.news.requests-per-second` (default 1).
   - Pace requests like `SecClient` (at most one a second), timeouts of 10 s to connect and 30 s to read, and send the `User-Agent` from `truesight.sec.user-agent`.
   - Failures throw `NewsClient.NewsAccessException("Google News could not be reached. Please try again later.")`.
- **`GoogleNewsParser`** (plain class, unit-tested): reads the RSS XML with external entities switched off (`disallow-doctype-decl`). Each `<item>`: `<title>` (remove the trailing " - " + the `<source>` text), `<link>`, `<source>`, `<pubDate>` (RFC 1123). Skip items with a missing title, link or date.
- **Search name:** the company's stored name without what follows a comma and without a trailing legal ending (Inc, Corp, Ltd, Co, plc, N.V., S.A., AG, SE, LLC), e.g. "Taiwan Semiconductor Manufacturing". Put this in one small method, `NewsSearchName.of(name)`, with a unit test.
- **`NewsCheckService.start(portfolioId)`** (checks the owner and the refusals, sets `CHECKING`, answers) and **`NewsCheckJob.run(portfolioId)`** (`@Async`): for each company, skip if `companies.news_checked_at` is within the last hour; otherwise search, keep the 10 newest from the last 7 days, insert those not already saved (`UNIQUE (company_id, url)`), and set `news_checked_at`. Then call every `NewsCheckStep` bean in order. Finally set the portfolio `IDLE` with `news_checked_at`, or `FAILED` with the reason. One company failing does not stop the others; if every search failed, the check is `FAILED`.
- **Website:** a `CheckNewsButton` component (reused by Get Alerts), placed on the portfolio page beside Analyse: the button, "Checking news…" while `CHECKING` (poll the portfolio every 2 seconds, like holdings), the last-checked time, or the failure reason. Add `newsCheck(portfolioId)` to `src/api/portfolios.ts`.
- **Tests:** `GoogleNewsParserTest` with `Examples.googleNewsTsmc()` (titles cleaned, dates parsed, count); `NewsSearchNameTest`; `NewsCheckIT` with a `@MockitoBean NewsClient` returning the parsed sample, for criteria 1–8; the button's states in a website test.

**Out of scope:** checking news automatically (Sprint 4); reading article text.
