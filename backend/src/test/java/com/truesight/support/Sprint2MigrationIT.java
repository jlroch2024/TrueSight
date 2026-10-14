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

/** Proves Sprint 2 can upgrade a Sprint 1 database that already contains user portfolio data. */
@IntegrationTest
class Sprint2MigrationIT {

    @Autowired
    DataSource dataSource;

    @Test
    void migrationTwoKeepsSprintOneRowsAndAppliesTheNewDefaults() throws Exception {
        String schema = "sprint2_upgrade_" + UUID.randomUUID().toString().replace("-", "");
        try {
            flyway(schema, MigrationVersion.fromVersion("1")).migrate();
            long userId;
            long portfolioId;
            long companyId;
            long reportId;
            try (Connection connection = dataSource.getConnection()) {
                String originalSchema = connection.getSchema();
                try {
                    connection.setSchema(schema);
                    userId = insertedId(connection, "INSERT INTO users (email) VALUES ('existing@example.com') RETURNING id");
                    portfolioId = insertedId(connection, "INSERT INTO portfolios (user_id, name) VALUES (" + userId
                            + ", 'Existing Portfolio') RETURNING id");
                    companyId = insertedId(connection, "INSERT INTO companies (name) VALUES ('Existing Company') RETURNING id");
                    insertedId(connection, "INSERT INTO holdings (portfolio_id, ticker) VALUES (" + portfolioId
                            + ", 'EXST') RETURNING id");
                    reportId = insertedId(connection, "INSERT INTO reports (company_id, form, accession_number, filing_date, url, text) "
                            + "VALUES (" + companyId + ", '10-K', 'existing-report', '2026-01-01', 'https://example.com', 'text') "
                            + "RETURNING id");
                    connection.createStatement().executeUpdate("INSERT INTO relationships "
                            + "(report_id, company_id, counterparty_id, type, evidence) VALUES (" + reportId + ", "
                            + companyId + ", " + companyId + ", 'SUPPLIER', 'Existing evidence')");
                } finally {
                    connection.setSchema(originalSchema);
                }
            }

            flyway(schema, null).migrate();

            try (Connection connection = dataSource.getConnection()) {
                String originalSchema = connection.getSchema();
                try {
                    connection.setSchema(schema);
                    try (Statement statement = connection.createStatement();
                         ResultSet result = statement.executeQuery("SELECT u.role, p.name, p.news_status, h.ticker, "
                                 + "r.review_status FROM users u JOIN portfolios p ON p.user_id = u.id "
                                 + "JOIN holdings h ON h.portfolio_id = p.id JOIN relationships r ON r.report_id = "
                                 + reportId + " WHERE u.id = " + userId)) {
                        assertThat(result.next()).isTrue();
                        assertThat(result.getString("role")).isEqualTo("PORTFOLIO_MANAGER");
                        assertThat(result.getString("name")).isEqualTo("Existing Portfolio");
                        assertThat(result.getString("news_status")).isEqualTo("IDLE");
                        assertThat(result.getString("ticker")).isEqualTo("EXST");
                        assertThat(result.getString("review_status")).isEqualTo("UNREVIEWED");
                        assertThat(result.next()).isFalse();
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
