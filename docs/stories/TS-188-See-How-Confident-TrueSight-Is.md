# TS-188 See How Confident TrueSight Is

| | |
|---|---|
| Type | Story |
| Status | To Do (in Jira on 23 October 2026) |
| Epic | [TS-5 Trust and Review](TS-5-Trust-and-Review.md) |
| Sprint | Sprint 3 |
| Points | 5 |
| Labels | s3-new, sprint-3 |
| Assignee | Lim Zhe An |

## User Story

As a portfolio manager, I want to see how confident TrueSight is about each relationship, and why, so that I know how much to rely on it.

## What We Agreed

- Each relationship has a **confidence, High, Medium or Low, with its reasons**, from three facts:
   - **The quote match:** how much of the AI's quote was found, in order, in the report. Strong at 95% or more, Partial from 85% (the checker's floor). Relationships found before this story have no recorded match: "Quote Match Not Recorded".
   - **The review:** Approved by an analyst.
   - **The second check:** the second AI Agrees or Doubts.
- **The rule:** Approved is High ("An Analyst Approved It"). Otherwise, a second-check doubt is Low; a strong match with the second check agreeing is High; anything else is Medium. Each reason is listed with its effect.
- **Shown** as a tag ("High Confidence") with its reasons in the Inspector, and on each linked holding of an alert's Holdings Reached.

## Acceptance Criteria

1. An Approved relationship is High, with "An Analyst Approved It".
1. An unreviewed relationship with a strong match and an agreeing second check is High; with a doubt, Low; otherwise Medium; each with its reasons.
1. A relationship found before this story shows "Quote Match Not Recorded" and is judged on the other two facts.
1. The Inspector shows the tag and reasons; alerts show the tag on each linked holding.

## Technical Notes

- `relationship/QuoteChecker.java`: add `match(String quote)` returning the evidence and the share of the quote's words found; `evidenceFor` uses it. `RelationshipFinder` saves the share in `relationships.quote_match`.
- One `RelationshipConfidence.of(relationship)` in the backend computes level and reasons; `RelationshipResponse` and `AlertResponse.ReachedHolding` gain `confidence: { level, reasons[] }`. The website never recomputes it.
- **Tests:** `QuoteCheckerTest` (the share, with real sentences from `nvidia-10k.txt`); `RelationshipConfidenceTest` for every rule; website tests for the tag and reasons.
