package com.truesight.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Starts a real PostgreSQL inside Docker for the tests, and throws it away afterwards.
 *
 * <p>It is the same PostgreSQL 16 the app uses everywhere else, so the tests check the real thing. A pretend
 * in-memory database would accept SQL that PostgreSQL rejects, and tests would pass for the wrong reasons.
 *
 * <p>{@code @ServiceConnection} points the app at this database automatically, with no address or password to copy.
 * One database is shared by every test class in a run, so its start-up cost is paid once. Docker must be running.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgres() {
        return new PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"));
    }
}
