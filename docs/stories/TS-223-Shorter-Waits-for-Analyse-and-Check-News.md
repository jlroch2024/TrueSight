# TS-223 Shorter Waits for Analyse and Check News

| | |
|---|---|
| Type | Story |
| Status | To Do (in Jira on 30 October 2026) |
| Epic | [TS-7 Running TrueSight](TS-7-Running-TrueSight.md) |
| Sprint | Sprint 4 |
| Points | 5 |
| Labels | s4-new, sprint-4 |

## User Story
As a portfolio manager, I want each holding to finish and each alert to be found as early as possible, so that I wait
as little as possible after pressing Analyse or Check News.

## What We Agreed

- **Asked for by the product owner on 30 October 2026.** Faster Analyse and Check News (TS-179) made the work run
  several at a time; this story removes the waits that remain, and makes the most important results come first.
- **A holding is Done when its relationships are saved.** The second check (a second AI reading each claim) runs
  afterwards, in the background. It already never fails the analysis; now it no longer holds up Done either. Until it
  answers, the relationship shows as it does today with no second check.
- **Check News works in three rounds, most important first:** the holdings' own news, then their direct suppliers',
  then the suppliers' suppliers'. After each round the headlines are judged and the alerts built, so alerts about
  holdings exist within seconds, and supplier alerts follow. Every round follows today's rules (7 days, at most 10
  articles a company, Google News at most once a second).
- **Suppliers' suppliers are asked less often:** a company that is only a supplier's supplier in this portfolio is not
  asked again within 6 hours; holdings and direct suppliers keep today's one hour. They are most of the companies, and
  Google News's one request a second is the longest wait in a news check.
- **Two portfolios' news checks never wait for each other.** Today Filter Out Noise holds one lock for the whole app
  (`NewsAssessor`), so a second portfolio's check waits for the first to finish. The lock becomes one per article, so
  an article is still judged only once.
- **The SEC's ticker list is downloaded once a day,** not once for every new ticker as now (`SecClient.companyForTicker`
  caches only the ticker it found).
- **This story is the backend only.** Showing results on the screen as they arrive, and keeping the graph smooth while
  it grows, is Grow the Graph as Results Arrive (its own story, the product owner's). Either can merge first.
- **Not in this story:** downloading SEC reports side by side is in Read Each Supplier's Own Report (TS-217), and
  recommendations are rewritten by One Suggestion per Event (TS-214); neither is touched here. How many holdings and
  headline batches run at once stays a setting (`ANALYSIS_PARALLEL`, `NEWS_PARALLEL_BATCHES`); raising it on the live
  site is a deployment choice, bounded by Gemini's request limit.

## Acceptance Criteria

1. A holding is Done as soon as its relationships are saved; its second check runs afterwards, and a failed second
   check never changes the holding.
1. Check News fetches the holdings' news first, then the direct suppliers', then the suppliers' suppliers', and builds
   alerts after each round: an alert about a holding exists before the suppliers' news has been fetched.
1. A company that is only a supplier's supplier in the portfolio is not asked again within 6 hours; a holding or a
   direct supplier is asked again after one hour.
1. Two portfolios' news checks judge their headlines at the same time, and an article shared by both is judged once.
1. Analysing ten new tickers downloads the SEC's ticker list once.

## Technical Notes

- **Second check after Done:** move `secondCheck.run(proven, report.getId())` out of `RelationshipFinder.findIn`, so
  it is no longer inside the holding's transaction. `AnalysisJob.analyze`, after `analysis.analyze` has committed the
  holding as Done, runs the second check for that report's relationships that have none yet, catching every failure
  as `SecondCheck` already does. `SecondCheckPassService` (the analyst-run pass) is unchanged.
- **News rounds:** `NewsCheckService.companiesFor` already works out the three groups (holdings, `directSuppliers`, then
  their suppliers); return them as three lists, for example a record `NewsRounds(holdings, suppliers,
  suppliersSuppliers)`, without changing which companies are checked. `NewsCheckJob.run` fetches and saves one round,
  then calls `steps.run(portfolioId)`, then the next round. Every `NewsCheckStep` already handles being run more than
  once: `NewsAssessor` judges only unjudged articles, and `AlertBuilder` skips an article the portfolio already has an
  alert for (`existsByPortfolioIdAndArticleId`). A round that fails to fetch every company does not stop the next.
  `NewsCheckService.wasCheckedWithinTheLastHour` takes the window: 6 hours for the third round, one hour otherwise.
- **News lock:** in `NewsAssessor.run`, replace the app-wide `synchronized (lock)` with a lock per article, as
  `RecommendationWriter` has (`articleLocks`): claim the unjudged articles, skip any another check has claimed, and
  re-read before saving so an article is never judged twice.
- **Ticker list:** `SecClient.companyForTicker` keeps the whole `company_tickers.json` as a map, loaded on first use
  and again when it is more than a day old. It still goes through `request`, so the 10-a-second pacing and the
  `User-Agent` are unchanged.
- **No table change,** no new settings, and no website change.
- **Tests:**
  - `AnnualReportAnalysisIT` with a fake `GeminiClient` serving the saved `docs/examples/gemini-nvidia.json`, whose
    second check blocks: the holding is Done before it answers, and stays Done when it fails (criterion 1).
  - `NewsCheckIT`, with a fake `NewsClient` serving the saved Google News samples, its dates moved relative to now as
    it does today: a fake that blocks on the first supplier's search shows the holding's alert already saved
    (criterion 2); the 6-hour and one-hour windows (criterion 3).
  - `NewsAssessorIT`: two portfolios sharing a company, checked at once with a fake `GeminiClient` that waits, both
    judging at the same time, the shared article judged once (criterion 4).
  - `SecClientTest`: ten tickers, one ticker-list request (criterion 5).
