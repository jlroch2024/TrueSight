package com.truesight.support;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the database is built the agreed way: only by Flyway's migrations, with exactly the Sprint 1 tables.
 *
 * <p>An extra table would mean something other than Flyway created it. When a later migration genuinely adds a
 * table, add its name to {@link #TABLES}.
 */
@IntegrationTest
class DatabaseSchemaIT {

    private static final List<String> TABLES = List.of(
            "companies", "company_names", "holdings", "portfolios", "relationships", "reports", "users");

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void flywayRanTheFirstMigration() {
        List<String> applied = jdbc.queryForList(
                "SELECT version FROM flyway_schema_history WHERE success = true", String.class);

        assertThat(applied).contains("1");
    }

    @Test
    void theTablesAreExactlyTheOnesTheMigrationsCreate() {
        List<String> tables = jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables "
                        + "WHERE table_schema = 'public' AND table_type = 'BASE TABLE' AND table_name <> "
                        + "'flyway_schema_history'",
                String.class);

        assertThat(tables).containsExactlyInAnyOrderElementsOf(TABLES);
    }
}
