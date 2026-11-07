-- Every table change Sprint 5 needs, in one migration. No Sprint 5 story adds another.

-- Dependency Figures From the Filings: the concentration figures a report's own XBRL tags, such as "Apple: 91% of
-- revenue". company_id is the company whose report it is. counterparty_id is the company named, or null when the
-- report does not name it ("Customer A", "Ten Largest Customers"); label is the name as the filing labels it.
CREATE TABLE concentrations (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    report_id       BIGINT        NOT NULL REFERENCES reports (id) ON DELETE CASCADE,
    company_id      BIGINT        NOT NULL REFERENCES companies (id) ON DELETE CASCADE,
    counterparty_id BIGINT        REFERENCES companies (id) ON DELETE CASCADE,
    label           VARCHAR(300)  NOT NULL,
    kind            VARCHAR(10)   NOT NULL CHECK (kind IN ('CUSTOMER', 'SUPPLIER')),
    measure         VARCHAR(12)   NOT NULL CHECK (measure IN ('REVENUE', 'RECEIVABLES', 'PURCHASES', 'OTHER')),
    share           NUMERIC(5, 4) NOT NULL CHECK (share >= 0 AND share <= 1),
    period_start    DATE,
    period_end      DATE          NOT NULL,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE INDEX concentrations_company_idx ON concentrations (company_id);
CREATE INDEX concentrations_counterparty_idx ON concentrations (counterparty_id);

-- When a report's figures were read (null: not yet). A report's figures are read once.
ALTER TABLE reports ADD COLUMN figures_read_at TIMESTAMPTZ;

-- Find Who Names My Holding: how a relationship was found. REPORT: by reading the whole report. NAME_SEARCH: by reading
-- the passages of another company's report that name a holding.
ALTER TABLE relationships ADD COLUMN found_by VARCHAR(12) NOT NULL DEFAULT 'REPORT'
    CHECK (found_by IN ('REPORT', 'NAME_SEARCH'));

-- When each company was last searched for by name, and how many filings the search gave. One row per company.
CREATE TABLE name_searches (
    company_id    BIGINT      PRIMARY KEY REFERENCES companies (id) ON DELETE CASCADE,
    searched_at   TIMESTAMPTZ NOT NULL,
    filings_found INT         NOT NULL CHECK (filings_found >= 0)
);

-- The AI call record gains the name search.
ALTER TABLE ai_calls DROP CONSTRAINT ai_calls_purpose_check;
ALTER TABLE ai_calls ADD CONSTRAINT ai_calls_purpose_check
    CHECK (purpose IN ('FIND_RELATIONSHIPS', 'JUDGE_NEWS', 'SECOND_CHECK', 'RECOMMEND', 'CRITICALITY', 'COMMON_NAMES',
                       'ASK', 'TIMELINE', 'HIGHLIGHT', 'NAME_SEARCH'));

-- Recover Work a Restart Interrupts: one row per running server, renewed every 30 seconds, and which server owns each
-- piece of running work. No foreign keys: an owner with no row here is a server that has gone.
CREATE TABLE workers (
    id           UUID PRIMARY KEY,
    last_seen_at TIMESTAMPTZ NOT NULL
);
ALTER TABLE holdings ADD COLUMN worker_id UUID;
ALTER TABLE portfolios ADD COLUMN news_worker_id UUID;
ALTER TABLE companies ADD COLUMN report_worker_id UUID;
