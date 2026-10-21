package com.truesight.support;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves Sprint 3 can upgrade a Sprint 2 database that already holds data, as the live site's does: every row is kept,
 * existing relationships read source = 'AI', and everything else new is empty.
 */
@IntegrationTest
class Sprint3MigrationIT {

    @Autowired
    DataSource dataSource;

    @Test
    void migrationThreeKeepsSprintTwoRowsAndAppliesTheNewDefaults() throws Exception {
        String schema = "sprint3_upgrade_" + UUID.randomUUID().toString().replace("-", "");
        try {
            flyway(schema, MigrationVersion.fromVersion("2")).migrate();
            long userId;
            long relationshipId;
            long articleId;
            try (Connection connection = dataSource.getConnection()) {
                String originalSchema = connection.getSchema();
                try {
                    connection.setSchema(schema);
                    userId = insertedId(connection, "INSERT INTO users (email) VALUES ('existing@example.com') "
                            + "RETURNING id");
                    long portfolioId = insertedId(connection, "INSERT INTO portfolios (user_id, name) VALUES ("
                            + userId + ", 'Existing Portfolio') RETURNING id");
                    long companyId = insertedId(connection, "INSERT INTO companies (name) VALUES ('Existing Company') "
                            + "RETURNING id");
                    long holdingId = insertedId(connection, "INSERT INTO holdings (portfolio_id, ticker, company_id) "
                            + "VALUES (" + portfolioId + ", 'EXST', " + companyId + ") RETURNING id");
                    long reportId = insertedId(connection, "INSERT INTO reports (company_id, form, accession_number, "
                            + "filing_date, url, text) VALUES (" + companyId + ", '10-K', 'existing-report', "
                            + "'2026-01-01', 'https://example.com', 'text') RETURNING id");
                    relationshipId = insertedId(connection, "INSERT INTO relationships (report_id, company_id, "
                            + "counterparty_id, type, evidence, review_status) VALUES (" + reportId + ", " + companyId
                            + ", " + companyId + ", 'SUPPLIER', 'Existing evidence', 'APPROVED') RETURNING id");
                    articleId = insertedId(connection, "INSERT INTO news_articles (company_id, url, title, "
                            + "published_at) VALUES (" + companyId + ", 'https://example.com/news', 'Existing news', "
                            + "now()) RETURNING id");
                    insertedId(connection, "INSERT INTO news_assessments (article_id, is_event, event_type, severity, "
                            + "confidence, reason) VALUES (" + articleId + ", true, 'PRODUCTION_STOP', 'HIGH', 'HIGH', "
                            + "'Existing reason') RETURNING id");
                    long alertId = insertedId(connection, "INSERT INTO alerts (portfolio_id, article_id, exposure) "
                            + "VALUES (" + portfolioId + ", " + articleId + ", 10) RETURNING id");
                    connection.createStatement().executeUpdate("INSERT INTO alert_holdings (alert_id, holding_id, "
                            + "relationship_id) VALUES (" + alertId + ", " + holdingId + ", " + relationshipId + ")");
                } finally {
                    connection.setSchema(originalSchema);
                }
            }

            flyway(schema, null).migrate();

            try (Connection connection = dataSource.getConnection()) {
                String originalSchema = connection.getSchema();
                try {
                    connection.setSchema(schema);
                    try (Statement statement = connection.createStatement()) {
                        try (ResultSet user = statement.executeQuery(
                                "SELECT email, display_name FROM users WHERE id = " + userId)) {
                            assertThat(user.next()).isTrue();
                            assertThat(user.getString("email")).isEqualTo("existing@example.com");
                            assertThat(user.getString("display_name")).isNull();
                        }
                        try (ResultSet relationship = statement.executeQuery("SELECT review_status, source, added_by, "
                                + "criticality, quote_match, second_check FROM relationships WHERE id = "
                                + relationshipId)) {
                            assertThat(relationship.next()).isTrue();
                            assertThat(relationship.getString("review_status")).isEqualTo("APPROVED");
                            assertThat(relationship.getString("source")).isEqualTo("AI");
                            assertThat(relationship.getObject("added_by")).isNull();
                            assertThat(relationship.getString("criticality")).isNull();
                            assertThat(relationship.getObject("quote_match")).isNull();
                            assertThat(relationship.getString("second_check")).isNull();
                        }
                        try (ResultSet assessment = statement.executeQuery("SELECT is_event, severity, decision, "
                                + "ai_is_event FROM news_assessments WHERE article_id = " + articleId)) {
                            assertThat(assessment.next()).isTrue();
                            assertThat(assessment.getBoolean("is_event")).isTrue();
                            assertThat(assessment.getString("severity")).isEqualTo("HIGH");
                            assertThat(assessment.getString("decision")).isNull();
                            assertThat(assessment.getObject("ai_is_event")).isNull();
                        }
                        try (ResultSet link = statement.executeQuery("SELECT relationship_id, second_relationship_id "
                                + "FROM alert_holdings")) {
                            assertThat(link.next()).isTrue();
                            assertThat(link.getLong("relationship_id")).isEqualTo(relationshipId);
                            assertThat(link.getObject("second_relationship_id")).isNull();
                        }
                        try (ResultSet empty = statement.executeQuery("SELECT (SELECT count(*) FROM recommendations) "
                                + "+ (SELECT count(*) FROM ai_calls) AS rows")) {
                            empty.next();
                            assertThat(empty.getLong("rows")).isZero();
                        }
                    }
                } finally {
                    connection.setSchema(originalSchema);
                }
            }
        } finally {
            try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
                statement.execute("DROP SCHEMA IF EXISTS \"" + schema + "\" CASCADE");
            }
        }
    }

    private Flyway flyway(String schema, MigrationVersion target) {
        var configuration = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .schemas(schema)
                .defaultSchema(schema);
        if (target != null) {
            configuration.target(target);
        }
        return configuration.load();
    }

    private long insertedId(Connection connection, String sql) throws Exception {
        try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(sql)) {
            result.next();
            return result.getLong(1);
        }
    }
}
