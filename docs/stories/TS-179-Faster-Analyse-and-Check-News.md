# TS-179 Faster Analyse and Check News

| | |
|---|---|
| Type | Story |
| Status | Done (in Jira on 29 October 2026) |
| Epic | [TS-7 Running TrueSight](TS-7-Running-TrueSight.md) |
| Sprint | Sprint 3 |
| Points | 5 |
| Labels | s3-new, sprint-3 |
| Assignee | Allan Kok Song Yeong |

## User Story

As a portfolio manager, I want Analyse and Check News to finish quickly, so that I can upload a portfolio and see its supply chain and alerts without waiting minutes.

## What We Agreed

- **Why it is slow (item 57).** Analyse reads one holding after another on one background thread (`AnalysisJob.java`, line 22), and each waits tens of seconds for Gemini to read its report, so a 10-holding CSV waits ten times in a row. The SEC is not the bottleneck: it allows 10 requests a second. Check News fetches Google News at 1 request a second on purpose (a public feed, which must not block us), then sends the headlines to Gemini in batches of 20, one batch after another (`NewsAssessor.java`, line 102).
- **Analyse several holdings at once:** the setting `truesight.analysis.parallel` (`ANALYSIS_PARALLEL`, default 4). A company is still never analysed twice at the same time, and SEC requests stay at 10 a second or fewer across all of them.
- **Check News sends its Gemini batches several at once:** `truesight.news.parallel-batches` (`NEWS_PARALLEL_BATCHES`, default 4). Google News stays at 1 request a second.
- The defaults stay under Gemini's request limit; if Gemini still refuses a request, today's handling applies (the holding is Failed with its reason, and Analyse retries Failed holdings).
- Holdings turn Done as each finishes, in any order; the page already shows them as they change.
- Setting either to 1 behaves exactly as today.

## Acceptance Criteria

1. With 10 holdings and a fake Gemini that answers after a delay, at most 4 holdings are being analysed at any moment, and more than one at once.
1. Two portfolios holding the same company never analyse it at the same time.
1. SEC requests never exceed 10 in any second, however many holdings run at once.
1. A news check with 60 headlines to judge sends its three Gemini batches at the same time (at most 4 at once), and every headline gets its judgement.
1. Google News is still asked at most once a second.
1. With both settings at 1, the work runs one at a time, as today.

## Technical Notes

- **Analyse:** `backend/src/main/java/com/truesight/portfolio/AnalysisJob.java`. `run` submits each holding to a bounded executor (a `ThreadPoolTaskExecutor` bean, core and maximum size from the setting) and waits for all of them (`CompletableFuture.allOf`). Keep the `synchronized` per-company lock inside each task. `analysis.analyze` is called on the injected `AnalysisService`, so each holding keeps its own transaction.
- **SEC pacing:** `SecClient.request` is `synchronized` and spaces requests by `intervalMillis`, which already holds across threads. Keep it, and add a test that proves it under several threads.
- **Check News:** `backend/src/main/java/com/truesight/news/NewsAssessor.java`, `run` (lines 95–107). Keep the `synchronized (lock)` around the whole run, and inside it submit the batches to a second bounded executor, waiting for all. `save` uses repository calls only, so it is safe from worker threads. `NewsClient`'s 1-a-second pacing is unchanged.
- **Settings:** add both to `application.yml` with comments, and to `.env.example`.
- **Tests:** a quick `AnalysisJobTest` with a fake `AnalysisService` that records how many run at once; an `NewsAssessorIT` with a fake `GeminiClient` doing the same; a `SecClient` pacing test with several threads. No test calls the SEC, Gemini or Google News.
