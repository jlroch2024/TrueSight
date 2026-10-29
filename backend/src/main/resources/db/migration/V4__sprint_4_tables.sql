-- Every table change Sprint 4 needs, in one migration. No Sprint 4 story adds another.

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

-- Highlight What Matters in a Quote: the phrases the AI picked out in a relationship's evidence, each checked word for
-- word and kept as where it starts and ends in the evidence. highlighted_at is when the evidence was last read for
-- them (null: not yet, or the evidence changed since).
CREATE TABLE relationship_highlights (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    relationship_id BIGINT       NOT NULL REFERENCES relationships (id) ON DELETE CASCADE,
    kind            VARCHAR(10)  NOT NULL CHECK (kind IN ('COMPANY', 'SUPPLIED', 'RELIANCE', 'FIGURE')),
    start_at        INT          NOT NULL CHECK (start_at >= 0),
    end_at          INT          NOT NULL,
    CHECK (end_at > start_at)
);

CREATE INDEX relationship_highlights_relationship_idx ON relationship_highlights (relationship_id);

ALTER TABLE relationships ADD COLUMN highlighted_at TIMESTAMPTZ;

-- Read Each Supplier's Own Report: how far reading a supplier's own annual report has got, for companies that are not
-- read as a holding. Null: never asked for.
ALTER TABLE companies ADD COLUMN report_status VARCHAR(20)
    CHECK (report_status IN ('WAITING', 'ANALYSING', 'DONE', 'FAILED', 'NO_REPORT_FOUND'));
ALTER TABLE companies ADD COLUMN report_status_reason VARCHAR(500);

-- The AI call record gains Ask TrueSight, the timeline and the highlights.
ALTER TABLE ai_calls DROP CONSTRAINT ai_calls_purpose_check;
ALTER TABLE ai_calls ADD CONSTRAINT ai_calls_purpose_check
    CHECK (purpose IN ('FIND_RELATIONSHIPS', 'JUDGE_NEWS', 'SECOND_CHECK', 'RECOMMEND', 'CRITICALITY', 'COMMON_NAMES',
                       'ASK', 'TIMELINE', 'HIGHLIGHT'));
