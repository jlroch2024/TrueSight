package com.truesight.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Put {@code @IntegrationTest} above a test class to run it against the whole app and a real database.
 *
 * <p>There are two kinds of test in this project:
 * <ul>
 *   <li><b>Unit tests</b>, class names ending in {@code Test}: one piece on its own, in milliseconds.</li>
 *   <li><b>Integration tests</b>, class names ending in {@code IT}: the whole app, a real PostgreSQL in Docker, and
 *       real requests. Slower, but they catch what unit tests cannot, such as a table that does not match its
 *       Java class. The class name must end in exactly {@code IT}, or it never runs.</li>
 * </ul>
 *
 * <p>This annotation bundles four: start the whole app ({@code @SpringBootTest}), allow requests without a real
 * network ({@code @AutoConfigureMockMvc}), use the "test" settings, which also creates the demo user
 * ({@code @ActiveProfiles}), and start the database and the {@link TestLogins} helper ({@code @Import}).
 *
 * <p>Every {@code /api/} address except health, sign up and log in needs a logged-in user: use {@link TestLogins}.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, TestLogins.class})
public @interface IntegrationTest {
}
