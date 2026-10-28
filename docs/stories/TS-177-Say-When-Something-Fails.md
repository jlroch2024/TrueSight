# TS-177 Say When Something Fails

| | |
|---|---|
| Type | Story |
| Status | Done (in Jira on 29 October 2026) |
| Epic | [TS-7 Running TrueSight](TS-7-Running-TrueSight.md) |
| Sprint | Sprint 3 |
| Points | 3 |
| Labels | s2-fix, sprint-3 |
| Assignee | Ben Aw Yong |

## User Story

As a user, I want TrueSight to tell me when something could not be loaded or checked, so that a missing piece is never mistaken for "there is nothing there".

## What We Agreed

- Found while reviewing the code on 20 October 2026: in several places a failure makes the page quietly leave something out, so it looks the same as when nothing went wrong. Each now says what failed, and offers Retry where trying again can help.
- **Header Portfolio list.** If the list cannot load, the header shows "Portfolios Could Not Load" and a Retry button where the list would be. Manage Portfolios still opens (its window already shows its own error).
- **Your account.** If `GET /api/auth/me` fails for any reason but 401 (which already logs you out), the header shows "Account Could Not Load" and Retry where the email sits. The email, the Analyst tag and the Review page appear once it loads.
- **Check News with some companies missed.** When some companies' news cannot be fetched but at least one company's can, the check still finishes, and says so under "News Checked …": "News for 2 of 25 companies could not be fetched: Carl Zeiss SMT, Fabrinet. Check News again to retry them." (A company that fails stays unchecked, so the next Check News already retries it.) When every company fails, the check fails as today.
- **Rename.** The Manage Portfolios window passes the renamed portfolio along with its announcement, so the Portfolio page shows the new name without a second request that could fail.
- **A quiet case in the code.** A Google News item without a readable date is still skipped (it cannot be placed in the 7-day window), but now logged as a warning with how many were skipped.
- **Handled elsewhere:** the News tab's own quiet cases (the Supply Chain page's portfolio request, the `?? 'LOW'` severity) leave with the News tab in Alerts Are the One Source of News, which also replaces the Portfolio page's article count with a count of open alerts.
- **Not in this story,** because they are deliberate and visible: a company's first letter in place of an unknown logo, the whole report when its sections cannot be found, "The AI's answer could not be used." as an assessment's reason, and Show in Report opening at the top when the browser cannot find the sentence.

## Acceptance Criteria

1. When `GET /api/portfolios` fails, the header shows "Portfolios Could Not Load" and Retry; Retry loads the list.
1. When `GET /api/auth/me` fails with a status other than 401, the header shows "Account Could Not Load" and Retry; after a successful Retry, the email, the Analyst tag (for an analyst) and Review appear.
1. A news check in which one company's Google News request fails and another's succeeds finishes with the News Checked line and the sentence naming the company missed; when every request fails, the check is Failed as today.
1. After a rename in Manage Portfolios, the Portfolio page shows the new name even if reloading the portfolio list fails.
1. A Google News feed with an item whose date cannot be read logs one warning with the number skipped, and never the item's content.

## Technical Notes

- **Header list:** `frontend/src/components/PortfolioPicker.tsx` lines 22–27. Replace `.catch(() => setPortfolios([]))` with a `loadError` state; render the message (muted, small) and a `secondary` Retry button calling `load`.
- **Account:** `frontend/src/components/Layout.tsx` line 27, `authApi.me().then(setMe).catch(() => setMe(null))`. Keep an error state; a 401 never reaches it, because `api()` already logs out on 401.
- **Check News (backend):** `backend/src/main/java/com/truesight/news/NewsCheckJob.java` lines 36–52. Collect the names of the companies whose `news.search` threw `NewsAccessException` (use `NewsSearchName.of(company.name())`, the name that was searched). If some succeeded, call `service.finish(portfolioId, now, reason)`, where `NewsCheckService.finish` (line 105) gains a `reason` parameter saved in the existing `news_status_reason` column (null when nothing was missed). No table change. Name at most five companies, then "and N more".
- **Check News (website):** `frontend/src/components/CheckNewsButton.tsx` lines 73–80. Show `newsStatusReason` under the News Checked line when the status is not `FAILED` (muted, small, not the red error style). `PortfolioResponse` already sends `newsStatusReason`.
- **Rename:** `frontend/src/api/portfolios.ts` lines 28–35. `portfolioChanges.announce(changed?: Portfolio)` sends the changed portfolio in the event (`CustomEvent`, `detail`); `listen` passes it to the listener. `ManagePortfolios.tsx`'s `run` (line 92) announces with the value a rename returns. `PortfolioPage.tsx` lines 58–67 use the portfolio from the event when its id matches, and drop their own `portfolioApi.list()` call and its empty `catch`.
- **Skipped news items:** `backend/src/main/java/com/truesight/news/GoogleNewsParser.java` lines 42–52. Count the items skipped for an unreadable date and log one warning per feed when the count is above zero.
- **Tests:** `PortfolioPicker` (a failing list, then Retry), `Layout.test.tsx` (a failing `me`, then Retry), `NewsCheckIT` (the fake `NewsClient` throws for one company only: status Idle, `newsCheckedAt` set, the reason names that company; throwing for all: Failed, as today), `CheckNewsButton` (the reason under the line), `ManagePortfolios.test.tsx` and `PortfolioPage.test.tsx` (the rename reaches the page with the list request failing), `GoogleNewsParserTest` (the item without a date is skipped).
