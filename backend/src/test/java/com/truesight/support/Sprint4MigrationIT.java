package com.truesight.support;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Proves Sprint 4 can upgrade a Sprint 3 database that already holds data, as the live site's does: every row is
 * kept, the new columns start empty, the new tables start empty, and the AI call record accepts the three new
 * purposes.
 */
@IntegrationTest
class Sprint4MigrationIT {

    @Autowired
    DataSource dataSource;

    @Test
    void migrationFourKeepsSprintThreeRowsAndAddsTheSprintFourTables() throws Exception {
        String schema = "sprint4_upgrade_" + UUID.randomUUID().toString().replace("-", "");
        try {
            flyway(schema, MigrationVersion.fromVersion("3")).migrate();
            long companyId;
            long relationshipId;
            long callId;
            try (Connection connection = dataSource.getConnection()) {
                String originalSchema = connection.getSchema();
                try {
                    connection.setSchema(schema);
                    companyId = insertedId(connection, "INSERT INTO companies (name) VALUES ('Existing Company') "
                            + "RETURNING id");
                    long reportId = insertedId(connection, "INSERT INTO reports (company_id, form, accession_number, "
                            + "filing_date, url, text) VALUES (" + companyId + ", '10-K', 'existing-report', "
                            + "'2026-01-01', 'https://example.com', 'text') RETURNING id");
                    relationshipId = insertedId(connection, "INSERT INTO relationships (report_id, company_id, "
                            + "counterparty_id, type, evidence, review_status) VALUES (" + reportId + ", " + companyId
                            + ", " + companyId + ", 'SUPPLIER', 'Existing evidence', 'APPROVED') RETURNING id");
                    callId = insertedId(connection, "INSERT INTO ai_calls (purpose, model, prompt, outcome, "
                            + "started_at, duration_ms) VALUES ('COMMON_NAMES', 'existing-model', 'Existing prompt', "
                            + "'ANSWERED', now(), 10) RETURNING id");
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
                        try (ResultSet company = statement.executeQuery("SELECT name, report_status, "
                                + "report_status_reason FROM companies WHERE id = " + companyId)) {
                            assertThat(company.next()).isTrue();
                            assertThat(company.getString("name")).isEqualTo("Existing Company");
                            assertThat(company.getString("report_status")).isNull();
                            assertThat(company.getString("report_status_reason")).isNull();
                        }
                        try (ResultSet relationship = statement.executeQuery("SELECT review_status, evidence, "
                                + "highlighted_at FROM relationships WHERE id = " + relationshipId)) {
                            assertThat(relationship.next()).isTrue();
                            assertThat(relationship.getString("review_status")).isEqualTo("APPROVED");
                            assertThat(relationship.getString("evidence")).isEqualTo("Existing evidence");
                            assertThat(relationship.getObject("highlighted_at")).isNull();
                        }
                        try (ResultSet call = statement.executeQuery("SELECT purpose, model FROM ai_calls "
                                + "WHERE id = " + callId)) {
                            assertThat(call.next()).isTrue();
                            assertThat(call.getString("purpose")).isEqualTo("COMMON_NAMES");
                            assertThat(call.getString("model")).isEqualTo("existing-model");
                        }
                        try (ResultSet empty = statement.executeQuery("SELECT (SELECT count(*) FROM "
                                + "past_relationships) + (SELECT count(*) FROM relationship_highlights) AS rows")) {
                            empty.next();
                            assertThat(empty.getLong("rows")).isZero();
                        }
                        try (ResultSet constraints = statement.executeQuery("SELECT count(*) FROM pg_constraint "
                                + "WHERE conname = 'ai_calls_purpose_check' AND conrelid = 'ai_calls'::regclass")) {
                            constraints.next();
                            assertThat(constraints.getLong(1)).isEqualTo(1);
                        }
                    }

                    for (String purpose : new String[] {"ASK", "TIMELINE", "HIGHLIGHT", "COMMON_NAMES"}) {
                        insertedId(connection, "INSERT INTO ai_calls (purpose, model, prompt, outcome, started_at, "
                                + "duration_ms) VALUES ('" + purpose + "', 'model', 'prompt', 'ANSWERED', now(), 10) "
                                + "RETURNING id");
                    }
                    assertThatThrownBy(() -> insertedId(connection, "INSERT INTO ai_calls (purpose, model, prompt, "
                            + "outcome, started_at, duration_ms) VALUES ('NOT_A_PURPOSE', 'model', 'prompt', "
                            + "'ANSWERED', now(), 10) RETURNING id")).isInstanceOf(SQLException.class);
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
