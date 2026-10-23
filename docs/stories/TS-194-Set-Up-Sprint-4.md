# TS-194 Set Up Sprint 4

| | |
|---|---|
| Type | Task |
| Status | To Do (in Jira on 23 October 2026) |
| Epic | [TS-7 Running TrueSight](TS-7-Running-TrueSight.md) |
| Sprint | Sprint 4 |
| Points | 2 |
| Labels | s4-new, sprint-4 |

## User Story

As the team, we want every Sprint 4 table change in place first, so that the Sprint 4 stories can be built at the same time without editing each other's code.

## What We Agreed

- **One migration,** `V4__sprint_4_tables.sql`**,** exactly as below. No Sprint 4 story adds another.
- **Older years get their own table.** Today the graph, alerts, the review queue and the news check read relationships from every stored report of a company; with one report per company that never mattered. The filing timeline stores older reports, so their relationships go in `past_relationships`, which only the timeline reads. Nothing else changes.
- `AGENTS.md` **gains a Sprint 4 section:** `past_relationships` is read only by the timeline; Ask TrueSight answers only from TrueSight's own data; the briefing uses OpenPDF and nothing else; the daily news endpoint is the one `/api/` address protected by a scheduler token instead of a login.

```
-- Every table change Sprint 4 needs, in one migration. No Sprint 4 story adds another.

-- Publish a Scenario: a named "this company stops supplying", for every portfolio manager to run.
CREATE TABLE scenarios (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name           VARCHAR(100)  NOT NULL,
    company_id     BIGINT        NOT NULL REFERENCES companies (id) ON DELETE CASCADE,
    description    VARCHAR(1000),
    published_by   BIGINT        REFERENCES users (id) ON DELETE SET NULL,
    published_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    unpublished_by BIGINT        REFERENCES users (id) ON DELETE SET NULL,
    unpublished_at TIMESTAMPTZ
);

-- SEC Filing Timeline: relationships found in a company's older annual reports. Read only by the timeline.
CREATE TABLE past_relationships (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    report_id       BIGINT       NOT NULL REFERENCES reports (id) ON DELETE CASCADE,
    company_id      BIGINT       NOT NULL REFERENCES companies (id) ON DELETE CASCADE,
    counterparty_id BIGINT       NOT NULL REFERENCES companies (id) ON DELETE CASCADE,
    type            VARCHAR(10)  NOT NULL CHECK (type IN ('SUPPLIER', 'CUSTOMER')),
    provides        VARCHAR(500),
    evidence        TEXT         NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX past_relationships_company_idx ON past_relationships (company_id);

-- The AI call record gains Ask TrueSight and the timeline.
ALTER TABLE ai_calls DROP CONSTRAINT ai_calls_purpose_check;
ALTER TABLE ai_calls ADD CONSTRAINT ai_calls_purpose_check
    CHECK (purpose IN ('FIND_RELATIONSHIPS', 'JUDGE_NEWS', 'SECOND_CHECK', 'RECOMMEND', 'CRITICALITY', 'COMMON_NAMES',
                       'ASK', 'TIMELINE'));
```

## Acceptance Criteria

1. A database with Sprint 3's data migrates to V4, and the backend starts with `ddl-auto: validate`.
1. `Scenario` and `PastRelationship` entities and repositories exist; `AiPurpose` has `ASK` and `TIMELINE`.
1. `AGENTS.md` has the Sprint 4 section.

## Technical Notes

- Entities in the `scenario` (new) and `relationship` packages. Check the constraint's real name on a V3 database before dropping it (`\d ai_calls`); PostgreSQL names a column's check `ai_calls_purpose_check`.
- **Tests:** a migration test from a database holding Sprint 3 rows.
