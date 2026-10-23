# TS-189 Second AI Checks the First

| | |
|---|---|
| Type | Story |
| Status | To Do (in Jira on 23 October 2026) |
| Epic | [TS-5 Trust and Review](TS-5-Trust-and-Review.md) |
| Sprint | Sprint 3 |
| Points | 5 |
| Labels | s3-new, sprint-3 |
| Assignee | isaiah |

## User Story

As an analyst, I want a second AI to check each relationship the first one found, so that my time goes to the doubtful ones.

## What We Agreed

- After a report's relationships are found and quote-checked, a **second Gemini call** reads each claim ("Sony buys from AMD: semi-custom SoC products") with its evidence sentence, and answers **Agrees** or **Doubts**, with a reason. It doubts when the sentence describes a plan, a past relationship, a possibility, a competitor, or anything that is not a current supplier or customer. The backlog's example: a partner the report only plans to work with.
- It sees only the sentence, not the whole report, so it is cheap: up to 30 claims per call.
- **Doubtful relationships come first** in the Analyst Desk's Relationships tab, among the unreviewed, tagged "Second Check Doubts" with the reason. The graph does not change until an analyst decides.
- **Relationships already found:** an analyst-run pass checks every relationship that has no second check yet.

## Acceptance Criteria

1. Analysing a report sends its quote-checked claims for a second check and saves Agrees or Doubts with the reason.
1. Unreviewed relationships the second check doubts come first in the Relationships tab, with their tag and reason.
1. The pass checks every relationship without a second check, and running it again sends nothing.
1. A failed or unreadable second check leaves the relationships saved, with no second check, for the pass to retry.
1. Portfolio managers get 403 from the pass.

## Technical Notes

- New `relationship/SecondCheck.java`, purpose `SECOND_CHECK`, schema `[{ "index": int, "verdict": "AGREES" | "DOUBTS", "reason": string }]`, called by `RelationshipFinder` after `relationships.saveAll`.
- **The pass:** `POST /api/relationships/second-check` (analysts only, 202, background).
- **Review order:** `ReviewService.list` (lines 43–54) sorts unreviewed doubts first, then by portfolios relying on them, as today. `ReviewItemResponse` gains `secondCheck` and `secondCheckReason`.
- **Test data:** run the second check once by hand on the relationships from `nvidia-10k.txt` and save the real answer in `docs/examples/`, with its README entry.
- **Tests:** `SecondCheckIT` with a fake `GeminiClient` serving the saved answer; `ReviewIT` (the order); the website's tag.
