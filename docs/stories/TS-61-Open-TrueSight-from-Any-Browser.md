# TS-61 Open TrueSight from Any Browser

| | |
|---|---|
| Type | Story |
| Status | Done (in Jira on 29 October 2026) |
| Epic | [TS-7 Running TrueSight](TS-7-Running-TrueSight.md) |
| Sprint | Sprint 1 |
| Points | 3 |
| Labels | sprint-1 |
| Assignee | Jeremy Roch |

## User Story
As a portfolio manager, I want to use TrueSight from a web link, so that I do not have to install anything.

## What We Agreed

- TrueSight runs on Google Cloud, in Singapore (asia-southeast1).
- The frontend is built and served by the backend, so there is one service and one web address.
- Keys and passwords are kept in Google Cloud's secret store, never in the code.
- Merging into main updates the live site automatically, but only after the tests pass.
- A spending alert is set on the Google Cloud account before the first demo.

## Acceptance Criteria

- The live link opens TrueSight, and a user can sign up and log in there.
- Merging a pull request into main updates the live site within 15 minutes, without anyone doing it by hand.
- The Swagger page works at the live link.
- The GitHub repository contains no keys or passwords.
- A spending alert exists on the Google Cloud account.

## Technical Notes

- Backend and frontend: one Docker image, built from the repository, run on Cloud Run. The React build is copied into the backend's static files.
- Database: Cloud SQL for PostgreSQL, reached through Cloud Run's built-in Cloud SQL connection.
- Secrets: Secret Manager, passed to Cloud Run as environment variables: `DATABASE_PASSWORD`, `JWT_SECRET` and `GEMINI_API_KEY`.
- Deploying: a GitHub Actions workflow runs on every push to main. After the tests pass, it builds the image and deploys it. The Google Cloud key it uses is kept in GitHub's encrypted secrets, never in a file.
- The README gives the live link and the steps to set everything up again.
- Set SPRING_PROFILES_ACTIVE=prod on Cloud Run, so the demo user and Demo Portfolio are never created in the live database.
Send every address that is not /api/... and not a file to index.html, so pages such as /portfolios/1 open directly.

**Needs First:** Set Up the Project
