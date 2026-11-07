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
 * Proves Sprint 5 can upgrade a Sprint 4 database that already holds data, as the live site's does: every row is
 * kept, every relationship from before reads REPORT, the new columns start empty, the new tables start empty, and the
 * AI call record accepts the name search.
 */
@IntegrationTest
class Sprint5MigrationIT {

    @Autowired
    DataSource dataSource;

    @Test
    void migrationFiveKeepsSprintFourRowsAndAddsTheSprintFiveTables() throws Exception {
        String schema = "sprint5_upgrade_" + UUID.randomUUID().toString().replace("-", "");
        try {
            flyway(schema, MigrationVersion.fromVersion("4")).migrate();
            long companyId;
            long reportId;
            long relationshipId;
            long callId;
            try (Connection connection = dataSource.getConnection()) {
                String originalSchema = connection.getSchema();
                try {
                    connection.setSchema(schema);
                    companyId = insertedId(connection, "INSERT INTO companies (name) VALUES ('Existing Company') "
                            + "RETURNING id");
                    reportId = insertedId(connection, "INSERT INTO reports (company_id, form, accession_number, "
                            + "filing_date, url, text) VALUES (" + companyId + ", '10-K', 'existing-report', "
                            + "'2026-01-01', 'https://example.com', 'text') RETURNING id");
                    relationshipId = insertedId(connection, "INSERT INTO relationships (report_id, company_id, "
                            + "counterparty_id, type, evidence, review_status) VALUES (" + reportId + ", " + companyId
                            + ", " + companyId + ", 'SUPPLIER', 'Existing evidence', 'APPROVED') RETURNING id");
                    callId = insertedId(connection, "INSERT INTO ai_calls (purpose, model, prompt, outcome, "
                            + "started_at, duration_ms) VALUES ('HIGHLIGHT', 'existing-model', 'Existing prompt', "
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
                        try (ResultSet company = statement.executeQuery("SELECT name, report_worker_id "
                                + "FROM companies WHERE id = " + companyId)) {
                            assertThat(company.next()).isTrue();
                            assertThat(company.getString("name")).isEqualTo("Existing Company");
                            assertThat(company.getObject("report_worker_id")).isNull();
                        }
                        try (ResultSet report = statement.executeQuery("SELECT accession_number, figures_read_at "
                                + "FROM reports WHERE id = " + reportId)) {
                            assertThat(report.next()).isTrue();
                            assertThat(report.getString("accession_number")).isEqualTo("existing-report");
                            assertThat(report.getObject("figures_read_at")).isNull();
                        }
                        try (ResultSet relationship = statement.executeQuery("SELECT review_status, evidence, "
                                + "found_by FROM relationships WHERE id = " + relationshipId)) {
                            assertThat(relationship.next()).isTrue();
                            assertThat(relationship.getString("review_status")).isEqualTo("APPROVED");
                            assertThat(relationship.getString("evidence")).isEqualTo("Existing evidence");
                            assertThat(relationship.getString("found_by")).isEqualTo("REPORT");
                        }
                        try (ResultSet call = statement.executeQuery("SELECT purpose, model FROM ai_calls "
                                + "WHERE id = " + callId)) {
                            assertThat(call.next()).isTrue();
                            assertThat(call.getString("purpose")).isEqualTo("HIGHLIGHT");
                            assertThat(call.getString("model")).isEqualTo("existing-model");
                        }
                        try (ResultSet empty = statement.executeQuery("SELECT (SELECT count(*) FROM "
                                + "concentrations) + (SELECT count(*) FROM name_searches) + (SELECT count(*) FROM "
                                + "workers) AS rows")) {
                            empty.next();
                            assertThat(empty.getLong("rows")).isZero();
                        }
                        try (ResultSet columns = statement.executeQuery("SELECT count(*) FROM "
                                + "information_schema.columns WHERE table_schema = '" + schema + "' AND "
                                + "((table_name = 'holdings' AND column_name = 'worker_id') OR "
                                + "(table_name = 'portfolios' AND column_name = 'news_worker_id'))")) {
                            columns.next();
                            assertThat(columns.getLong(1)).isEqualTo(2);
                        }
                        try (ResultSet constraints = statement.executeQuery("SELECT count(*) FROM pg_constraint "
                                + "WHERE conname = 'ai_calls_purpose_check' AND conrelid = 'ai_calls'::regclass")) {
                            constraints.next();
                            assertThat(constraints.getLong(1)).isEqualTo(1);
                        }
                    }

                    for (String purpose : new String[] {"NAME_SEARCH", "HIGHLIGHT", "FIND_RELATIONSHIPS"}) {
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
