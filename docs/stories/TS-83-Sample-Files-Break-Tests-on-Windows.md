# TS-83 Sample Files Break Tests on Windows

| | |
|---|---|
| Type | Bug |
| Status | Done (in Jira on 23 October 2026) |
| Epic | [TS-7 Running TrueSight](TS-7-Running-TrueSight.md) |
| Sprint | Sprint 1 |
| Assignee | Jeremy Roch |

## Bug

ReportSectionTest fails on Windows laptops. Git converts docs/examples/ to Windows line endings on checkout, which adds one character per line, and the test counts characters. Keep those files with Unix line endings on every computer.
