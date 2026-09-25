-- Every table Sprint 1 needs, created in one go.
--
-- Flyway runs each file in this folder once, in number order, and remembers which it has run. So once this file is
-- merged it is never edited: an edit would do nothing on anybody's database that already ran it. A change goes in a
-- new file, V2__what_it_does.sql.
--
-- Sprint 1 stories do not add migrations of their own. If a story needs a change, agree it with the team first, so
-- two people do not both write V2.

-- People who can log in. password_hash is empty only for the demo user, which exists on laptops and in tests,
-- never on the live site.
CREATE TABLE users (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email         VARCHAR(320) NOT NULL UNIQUE,
    password_hash VARCHAR(100),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- A named group of holdings, belonging to one user. Names are unique within a user's account.
CREATE TABLE portfolios (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id    BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    name       VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (user_id, name)
);

-- Any business TrueSight knows about, whether anybody holds it or not. cik is the SEC's company number, when the
-- company files with the SEC; it is the most reliable way to tell two companies apart.
CREATE TABLE companies (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    cik        VARCHAR(10)  UNIQUE,
    ticker     VARCHAR(20),
    name       VARCHAR(300) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Every name a company has been seen under ("Taiwan Semiconductor Manufacturing Company Limited", "TSMC"), so the
-- same company is shown once. name_key is the name tidied for comparison; each tidied name belongs to one company.
CREATE TABLE company_names (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id BIGINT       NOT NULL REFERENCES companies (id) ON DELETE CASCADE,
    name       VARCHAR(300) NOT NULL,
    name_key   VARCHAR(300) NOT NULL UNIQUE
);

-- A company's annual report: a 10-K (US) or a 20-F (foreign), as plain text. Reports belong to the company, not to
-- any portfolio, because they are public: one report is downloaded once, whoever holds the company.
CREATE TABLE reports (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id       BIGINT       NOT NULL REFERENCES companies (id) ON DELETE CASCADE,
    form             VARCHAR(10)  NOT NULL CHECK (form IN ('10-K', '20-F')),
    accession_number VARCHAR(25)  NOT NULL UNIQUE,
    filing_date      DATE         NOT NULL,
    url              VARCHAR(500) NOT NULL,
    text             TEXT         NOT NULL,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- A company in a portfolio. company_id stays empty until the analysis has matched the ticker to a company.
-- status is where the analysis has got for this holding; status_reason says why, when it Failed.
CREATE TABLE holdings (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    portfolio_id  BIGINT        NOT NULL REFERENCES portfolios (id) ON DELETE CASCADE,
    ticker        VARCHAR(20)   NOT NULL,
    weight        NUMERIC(9, 4),
    company_id    BIGINT        REFERENCES companies (id),
    status        VARCHAR(20)   NOT NULL DEFAULT 'WAITING'
                  CHECK (status IN ('WAITING', 'ANALYSING', 'DONE', 'FAILED', 'NO_REPORT_FOUND')),
    status_reason VARCHAR(1000),
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT now()
);

-- A supplier or customer relationship stated in a report. company_id is the company whose report it came from;
-- counterparty_id is the other company; type says which way round: SUPPLIER means the counterparty supplies the
-- company, CUSTOMER means the counterparty buys from it. evidence is the report's own sentence, never the AI's.
CREATE TABLE relationships (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    report_id       BIGINT        NOT NULL REFERENCES reports (id) ON DELETE CASCADE,
    company_id      BIGINT        NOT NULL REFERENCES companies (id) ON DELETE CASCADE,
    counterparty_id BIGINT        NOT NULL REFERENCES companies (id) ON DELETE CASCADE,
    type            VARCHAR(10)   NOT NULL CHECK (type IN ('SUPPLIER', 'CUSTOMER')),
    provides        VARCHAR(500),
    evidence        TEXT          NOT NULL,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE INDEX holdings_portfolio_idx ON holdings (portfolio_id);
CREATE INDEX reports_company_idx ON reports (company_id);
CREATE INDEX relationships_company_idx ON relationships (company_id);
CREATE INDEX relationships_counterparty_idx ON relationships (counterparty_id);
