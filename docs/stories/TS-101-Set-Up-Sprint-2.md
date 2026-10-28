# TS-101 Set Up Sprint 2

| | |
|---|---|
| Type | Task |
| Status | Done (in Jira on 29 October 2026) |
| Epic | [TS-7 Running TrueSight](TS-7-Running-TrueSight.md) |
| Sprint | Sprint 2 |
| Labels | sprint-2 |
| Assignee | Allan Kok Song Yeong |

**Owner:** Allan · **Needs First:** the interface stories

**What it is.** The shared pieces that two or more Sprint 2 stories need at the same time, built once before anybody starts, exactly as Set Up the Project did for Sprint 1. It builds no feature on its own.

## What We Agreed

- One migration holds every Sprint 2 table change, so nobody else writes a migration.
- Roles are checked against the database on every request, not only the token, so changing a role takes effect at once.
- Every news step plugs into the news check through one interface, so the four news stories never edit each other's files.
- Sample data is real: a saved Google News response and a saved Gemini answer, each described in the README.

## Contents (each is a sub-task)

1. `backend/src/main/resources/db/migration/V2__sprint_2_tables.sql`, exactly:

```sql
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
```

1. **Entities and repositories**, matching the migration (Hibernate validates them):
   - `user`: `Role` enum (`PORTFOLIO_MANAGER`, `ANALYST`); `User.role` (`@Enumerated(STRING)`, default `PORTFOLIO_MANAGER`).
   - `relationship`: `ReviewStatus` enum; `Relationship.reviewStatus`, `reviewedBy` (Long), `reviewedAt` (Instant), `reviewNote`.
   - `portfolio`: `NewsStatus` enum (`IDLE`, `CHECKING`, `FAILED`); `Portfolio.newsStatus`, `newsStatusReason`, `newsCheckedAt`.
   - `company`: `Company.newsCheckedAt`.
   - New package `com.truesight.news`: `NewsArticle`, `NewsAssessment`, `Alert`, `AlertHolding` (composite key with `@IdClass`), enums `EventType`, `Severity`, `Confidence`, `AlertStatus`, and a repository for each.
1. `ApiException.forbidden(message)` (403). `GlobalExceptionHandler` already turns any `ApiException` into its status; add a test for 403 in `GlobalExceptionHandlerTest`. 403 is only for an action the user's **role** does not allow; another user's portfolio stays 404.
1. **Roles in the login.** `TokenService` adds a `role` claim (for the website's display only). `CurrentUser` gains `role()`, which **reads the user's role from the database**, so a change of role applies on the next request.
1. `NewsCheckStep`, in `com.truesight.news`: `public interface NewsCheckStep { void run(Long portfolioId); }`. Get News runs every `NewsCheckStep` bean, in `@Order`, after it saves new articles: Filter Out Noise is `@Order(1)`, See What a News Event Affects is `@Order(2)`. This is how the news stories connect without editing each other's code.
1. `GeminiClient.generate(String prompt, Map<String, Object> responseSchema)`: the same call with a schema of the caller's choosing. The existing `generate(prompt)` keeps using the relationship schema.
1. **Real sample data in** `docs/examples/`, each listed in its README with where and when it came from:
   - `google-news-tsmc.xml`: one real, unchanged Google News RSS response for `https://news.google.com/rss/search?q=%22Taiwan%20Semiconductor%20Manufacturing%22+when:7d&hl=en-US&gl=US&ceid=US:en`.
   - `gemini-news-assessment.json`: one real, unchanged Gemini answer to the Filter Out Noise prompt (in that story's Technical Notes) for the first 20 headlines of that response.
   - `Examples.java` gains `googleNewsTsmc()` and `geminiNewsAssessment()`.
1. **Test and laptop accounts.** `DemoUserSeeder` also creates `analyst@truesight.local` (password `truesight-demo`) with role `ANALYST`, in `local` and `test` only. `TestLogins` gains `analystBearer(email)`, which creates the user as an Analyst.
1. **Website plumbing.** `paths.ts`: `review: '/review'` and `alerts: (portfolioId) => /portfolios/${portfolioId}/alerts` (not yet in the sidebar). `navigation.ts`: an empty `ANALYST_NAV_ITEMS`, shown by `Layout` only to Analysts (read from `GET /api/auth/me`, see Analyst Role).
1. `DatabaseSchemaIT.TABLES` gains the four new tables. **AGENTS.md** gains a Sprint 2 section: the terms above, the rule that no story adds a migration, the `NewsCheckStep` pattern, 403 for roles, and "Analyst decisions apply to every user".

**Done when:** `./mvnw verify` and `npm test` pass; the app starts on a database that already holds Sprint 1 data, and every existing page still works; the sample files are real and documented.
