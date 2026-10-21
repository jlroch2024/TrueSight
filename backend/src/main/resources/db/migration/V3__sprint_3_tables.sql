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
