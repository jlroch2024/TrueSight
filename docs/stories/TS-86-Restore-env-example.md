# TS-86 Restore .env.example

| | |
|---|---|
| Type | Bug |
| Status | Done (in Jira on 29 October 2026) |
| Epic | [TS-7 Running TrueSight](TS-7-Running-TrueSight.md) |
| Sprint | Sprint 1 |
| Assignee | Jeremy Roch |

## What Happens
`.env.example` is missing from `main`. The README tells new teammates to copy it to `.env`, so the project can't be set up from the README.

## Why
TS-54's bugfix pull request (#11) deleted it by mistake. Its description talked about the line-ending fix, but its only change was the deletion. Nobody reviewed it, so it merged. Found in the Sprint 1 acceptance audit (Set Up the Project, AC6).

## Acceptance Criteria

- `.env.example` is back, exactly as it was before it was deleted.
