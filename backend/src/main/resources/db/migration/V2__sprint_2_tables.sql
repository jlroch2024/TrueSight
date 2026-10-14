-- Every table change Sprint 2 needs, in one migration. No Sprint 2 story adds another.
ALTER TABLE users ADD COLUMN role VARCHAR(20) NOT NULL DEFAULT 'PORTFOLIO_MANAGER'
    CHECK (role IN ('PORTFOLIO_MANAGER', 'ANALYST'));

ALTER TABLE relationships
    ADD COLUMN review_status VARCHAR(10) NOT NULL DEFAULT 'UNREVIEWED'
        CHECK (review_status IN ('UNREVIEWED', 'APPROVED', 'REJECTED')),
    ADD COLUMN reviewed_by BIGINT REFERENCES users (id) ON DELETE SET NULL,
    ADD COLUMN reviewed_at TIMESTAMPTZ,
    ADD COLUMN review_note VARCHAR(500);

ALTER TABLE portfolios
    ADD COLUMN news_status VARCHAR(10) NOT NULL DEFAULT 'IDLE' CHECK (news_status IN ('IDLE', 'CHECKING', 'FAILED')),
    ADD COLUMN news_status_reason VARCHAR(500),
    ADD COLUMN news_checked_at TIMESTAMPTZ;

-- When TrueSight last asked the news source about this company, for the one-hour rule.
ALTER TABLE companies ADD COLUMN news_checked_at TIMESTAMPTZ;

-- A headline found for a company. Shared, like reports: news is public.
CREATE TABLE news_articles (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id   BIGINT        NOT NULL REFERENCES companies (id) ON DELETE CASCADE,
    url          VARCHAR(2000) NOT NULL,
    title        VARCHAR(1000) NOT NULL,
    source       VARCHAR(200),
    published_at TIMESTAMPTZ   NOT NULL,
    created_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    UNIQUE (company_id, url)
);

-- The AI's judgement of one article. Shared, one per article.
CREATE TABLE news_assessments (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    article_id BIGINT       NOT NULL UNIQUE REFERENCES news_articles (id) ON DELETE CASCADE,
    is_event   BOOLEAN      NOT NULL,
    event_type VARCHAR(30)  CHECK (event_type IN ('PRODUCTION_STOP', 'SUPPLY_SHORTAGE', 'LABOUR_ACTION',
                                                   'NATURAL_DISASTER', 'TRADE_RESTRICTION', 'FINANCIAL_DISTRESS',
                                                   'LEGAL_OR_REGULATORY', 'CYBER_ATTACK', 'OTHER_DISRUPTION')),
    severity   VARCHAR(10)  CHECK (severity IN ('LOW', 'MEDIUM', 'HIGH')),
    confidence VARCHAR(10)  NOT NULL CHECK (confidence IN ('LOW', 'MEDIUM', 'HIGH')),
    summary    VARCHAR(500),
    reason     VARCHAR(500) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CHECK ((is_event AND event_type IS NOT NULL AND severity IS NOT NULL)
        OR (NOT is_event AND event_type IS NULL AND severity IS NULL))
);

-- One event reaching one portfolio. Private to the portfolio.
CREATE TABLE alerts (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    portfolio_id BIGINT        NOT NULL REFERENCES portfolios (id) ON DELETE CASCADE,
    article_id   BIGINT        NOT NULL REFERENCES news_articles (id) ON DELETE CASCADE,
    exposure     NUMERIC(9, 4) NOT NULL,
    status       VARCHAR(12)   NOT NULL DEFAULT 'NEW' CHECK (status IN ('NEW', 'ACKNOWLEDGED', 'DISMISSED')),
    created_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    UNIQUE (portfolio_id, article_id)
);

-- Each holding an alert reaches, and the relationship that links it (empty when the event is about the holding itself).
CREATE TABLE alert_holdings (
    alert_id        BIGINT NOT NULL REFERENCES alerts (id) ON DELETE CASCADE,
    holding_id      BIGINT NOT NULL REFERENCES holdings (id) ON DELETE CASCADE,
    relationship_id BIGINT REFERENCES relationships (id) ON DELETE CASCADE,
    PRIMARY KEY (alert_id, holding_id)
);

CREATE INDEX news_articles_company_idx ON news_articles (company_id);
CREATE INDEX alerts_portfolio_idx ON alerts (portfolio_id);
