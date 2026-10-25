# TS-182 Set Up Sprint 3

| | |
|---|---|
| Type | Task |
| Status | Done (in Jira on 23 October 2026) |
| Epic | [TS-7 Running TrueSight](TS-7-Running-TrueSight.md) |
| Sprint | Sprint 3 |
| Points | 5 |
| Labels | s3-new, sprint-3 |
| Assignee | GOH ZYIK XYUN |

## User Story

As the team, we want every Sprint 3 table change, the AI call record and the Analyst Desk's frame in place first, so that the Sprint 3 stories can be built at the same time without editing each other's code.

## What We Agreed

- **One migration,** `V3__sprint_3_tables.sql`**,** with every Sprint 3 table change, exactly as below. No Sprint 3 story adds another. Existing rows get the defaults shown, so the live site's data carries over.
- **Every Gemini call is recorded** in `ai_calls`: its purpose, model, prompt, answer, outcome and time taken. A report's text is not copied into the record: the prompt keeps its place with a marker and the record names the report.
- **The Analyst Desk:** the Review page (`/review`) gains a row of tabs. The setup adds the frame with one tab, **Relationships**, which is today's review list unchanged. Each later story adds its own tab. The sidebar's "Review" becomes "Analyst Desk".
- `AGENTS.md` **gains a Sprint 3 section** with the rules below.

```
-- Every table change Sprint 3 needs, in one migration. No Sprint 3 story adds another.

-- Edit My Profile: the name shown instead of the email.
ALTER TABLE users ADD COLUMN display_name VARCHAR(100);

-- Fix or Add a Relationship, How Critical Each Supplier Is, See How Confident TrueSight Is, Second AI Checks the First.
ALTER TABLE relationships
    ADD COLUMN source               VARCHAR(10)  NOT NULL DEFAULT 'AI' CHECK (source IN ('AI', 'ANALYST')),
    ADD COLUMN added_by             BIGINT       REFERENCES users (id) ON DELETE SET NULL,
    ADD COLUMN edited_by            BIGINT       REFERENCES users (id) ON DELETE SET NULL,
    ADD COLUMN edited_at            TIMESTAMPTZ,
    ADD COLUMN criticality          VARCHAR(15)  CHECK (criticality IN ('SOLE_SOURCE', 'SINGLE_SOURCE')),
    ADD COLUMN criticality_evidence TEXT,
    ADD COLUMN quote_match          NUMERIC(4, 3) CHECK (quote_match BETWEEN 0 AND 1),
    ADD COLUMN second_check         VARCHAR(10)  CHECK (second_check IN ('AGREES', 'DOUBTS')),
    ADD COLUMN second_check_reason  VARCHAR(500),
    ADD COLUMN second_checked_at    TIMESTAMPTZ;

-- Confirm or Overrule an Event: an analyst's decision. The AI's own answer is copied into ai_* the first time an
-- analyst changes it, so the columns the rest of TrueSight reads always hold the judgement in force.
ALTER TABLE news_assessments
    ADD COLUMN ai_is_event   BOOLEAN,
    ADD COLUMN ai_event_type VARCHAR(30),
    ADD COLUMN ai_severity   VARCHAR(10),
    ADD COLUMN decision      VARCHAR(10)  CHECK (decision IN ('CONFIRMED', 'OVERRULED')),
    ADD COLUMN decided_by    BIGINT       REFERENCES users (id) ON DELETE SET NULL,
    ADD COLUMN decided_at    TIMESTAMPTZ,
    ADD COLUMN decision_note VARCHAR(500);

-- Two Steps Along the Graph: the second link, when an event reaches a holding through a supplier's supplier.
ALTER TABLE alert_holdings
    ADD COLUMN second_relationship_id BIGINT REFERENCES relationships (id) ON DELETE CASCADE;

-- Get a Recommendation: one suggested next step per event. Shared, like the event itself.
CREATE TABLE recommendations (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    article_id  BIGINT        NOT NULL UNIQUE REFERENCES news_articles (id) ON DELETE CASCADE,
    text        VARCHAR(1000) NOT NULL,
    edited_text VARCHAR(1000),
    status      VARCHAR(10)   NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'APPROVED', 'WITHHELD')),
    note        VARCHAR(500),
    decided_by  BIGINT        REFERENCES users (id) ON DELETE SET NULL,
    decided_at  TIMESTAMPTZ,
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT now()
);

-- AI Audit Trail: every call to Gemini.
CREATE TABLE ai_calls (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    purpose     VARCHAR(30)  NOT NULL CHECK (purpose IN ('FIND_RELATIONSHIPS', 'JUDGE_NEWS', 'SECOND_CHECK',
                                                          'RECOMMEND', 'CRITICALITY', 'COMMON_NAMES')),
    model       VARCHAR(100) NOT NULL,
    prompt      TEXT         NOT NULL,
    report_id   BIGINT       REFERENCES reports (id) ON DELETE SET NULL,
    response    TEXT,
    outcome     VARCHAR(12)  NOT NULL CHECK (outcome IN ('ANSWERED', 'UNREADABLE', 'FAILED')),
    error       VARCHAR(500),
    kept        INTEGER,
    dropped     INTEGER,
    started_at  TIMESTAMPTZ  NOT NULL,
    duration_ms INTEGER      NOT NULL
);

CREATE INDEX ai_calls_started_idx ON ai_calls (started_at);
CREATE INDEX recommendations_status_idx ON recommendations (status);
```

## Acceptance Criteria

1. A fresh database and a copy of the Sprint 2 schema with data both migrate to V3; existing relationships read `source = 'AI'`, and everything else new is empty.
1. The backend starts with `ddl-auto: validate`, with entities for every new table and column.
1. Every Gemini call (finding relationships, judging news) writes one `ai_calls` row with its purpose, outcome and time taken, whether Gemini answers, answers unreadably, or fails; a report's text is never stored in it.
1. `/review` shows the Analyst Desk with a Relationships tab that works exactly as the Review page does today; the sidebar says "Analyst Desk".
1. `AGENTS.md` has the Sprint 3 section.

## Technical Notes

- **Entities:** new `Recommendation` (package `news`) and `AiCall` (package `ai`, new), with repositories; new fields on `User`, `Relationship`, `NewsAssessment` and `AlertHolding`; enums `RelationshipSource`, `Criticality`, `SecondCheck`, `AssessmentDecision`, `RecommendationStatus`, `AiPurpose`, `AiOutcome`.
- **Recording calls:** `relationship/GeminiClient.java`. `generate` takes an `AiRequest(AiPurpose purpose, String prompt, Long reportId, String recordedPrompt)` and returns `AiAnswer(String text, Long callId)`. `recordedPrompt` is the prompt with any report text replaced by `[report {id}: {n} characters]`. A new `ai/AiCallLog` saves the row in its own transaction (`REQUIRES_NEW`), so a failed call is still recorded, and offers `finish(callId, kept, dropped)` and `unreadable(callId)` for callers. Update `RelationshipFinder` and `NewsAssessor` to the new signature; the relationship finder reports how many quotes it kept and dropped. Never log or record the API key.
- **Analyst Desk frame:** new `frontend/src/pages/AnalystDeskPage.tsx` at `paths.review`. Tabs are buttons with `role="tab"`, and the open tab is in the address (`?tab=relationships`), so it survives a reload. The Relationships tab renders today's `ReviewPage` content unchanged (Easier Review edits that file; keep them apart). In `navigation.ts`, `ANALYST_NAV_ITEMS`' label becomes "Analyst Desk".
- `AGENTS.md`**, "Sprint 3 Shared Rules":** all Sprint 3 table changes are in `V3__sprint_3_tables.sql`; every Gemini call goes through `GeminiClient` with an `AiPurpose`, so it is recorded; analyst tools are tabs of the Analyst Desk, never new pages; a portfolio manager's screen shows only what they act on; recommendations are research support, never investment advice (no buy, sell or hold, no price targets).
- **Tests:** a migration test against a database holding Sprint 2 rows; `GeminiClientTest` (one row per call for each outcome, against a fake server; no report text stored); `AnalystDeskPage.test.tsx` (the tab in the address; the Relationships tab).
