# TS-211 Get News Automatically

| | |
|---|---|
| Type | Story |
| Status | To Do (in Jira on 29 October 2026) |
| Epic | [TS-6 News and Alerts](TS-6-News-and-Alerts.md) |
| Sprint | Sprint 4 |
| Points | 3 |
| Labels | s4-new, sprint-4 |

## User Story
As a portfolio manager, I want TrueSight to check the news every day, so that I do not have to remember to press the button.

## What We Agreed
- **Every day at 7:00 Singapore time,** TrueSight checks the news for every portfolio with at least one analysed holding, with the same rules as Check News (a company checked in the last hour is not asked again).
- **Started from outside the app:** the live site on Cloud Run sleeps between requests, so a timer inside the app would not fire reliably. Google Cloud Scheduler calls `POST /api/news-checks/daily` once a day.
- **That address takes no login:** it needs the scheduler token instead (a secret, `NEWS_SCHEDULER_TOKEN`). A wrong or missing token is refused with 401, and nothing runs.
- A daily check shows like any other: the Alerts tab's "News Checked 3 Hours Ago", and its new alerts appear as usual.
- **It costs** one Gemini call per 20 new headlines across all portfolios each day.
- The setting `truesight.news.daily-enabled` (default true) turns it off.

## Acceptance Criteria
1. A request with the right token starts a news check for every portfolio with an analysed holding, and answers 202.
1. A wrong or missing token is refused with 401, and nothing runs.
1. Each portfolio's News Checked time and alerts update as after Check News.
1. With the setting off, the endpoint answers 202 and checks nothing.
1. `docs/deployment.md` gives the exact commands to create the secret and the Cloud Scheduler job.

## Technical Notes
- **Endpoint:** `news/DailyNewsController.java`, `POST /api/news-checks/daily`, reading the `X-Scheduler-Token` header and comparing it with `truesight.news.scheduler-token` in constant time (`MessageDigest.isEqual`). In `config/SecurityConfig.java` this address is `permitAll()` (line 69); the token is checked in the controller. Never log the token.
- **Work:** for each portfolio with an analysed holding, the same `NewsCheckJob` as Check News, one portfolio after another.
- **Settings:** `application.yml` and `.env.example` gain `NEWS_SCHEDULER_TOKEN` and `NEWS_DAILY_ENABLED`.
- **`docs/deployment.md`:** the commands the product owner runs: `gcloud secrets create NEWS_SCHEDULER_TOKEN`, giving the service account access, `gcloud run services update truesight --update-secrets=…`, and `gcloud scheduler jobs create http truesight-daily-news --location asia-southeast1 --schedule "0 7 * * *" --time-zone "Asia/Singapore" --uri "https://…/api/news-checks/daily" --http-method POST --headers "X-Scheduler-Token=…"`.
- **Tests:** `DailyNewsIT` (each criterion, with a fake `NewsClient`).
