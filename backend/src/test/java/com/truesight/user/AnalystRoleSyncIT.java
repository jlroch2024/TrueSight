package com.truesight.user;

import com.truesight.support.IntegrationTest;
import com.truesight.support.TestLogins;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Acceptance Criterion 2 of Analyst Role: adding an existing user's email to {@code ANALYST_EMAILS} makes them an
 * Analyst once the app restarts, and removing it makes them a Portfolio Manager again.
 *
 * <p>A restart is a new {@link AnalystRoleSync} run with the setting as it then is, so each test builds one with the
 * list it wants and runs it.
 */
@IntegrationTest
class AnalystRoleSyncIT {

    @Autowired
    UserRepository users;

    @Autowired
    TestLogins logins;

    private void restartWith(String analystEmails) {
        new AnalystRoleSync(users, new AnalystEmails(analystEmails)).run(null);
    }

    private Role roleOf(String email) {
        return users.findByEmail(email).orElseThrow().getRole();
    }

    @Test
    void addingAnExistingUsersEmailMakesThemAnAnalystAndRemovingItMakesThemAPortfolioManagerAgain() {
        logins.user("sync-first@example.com");
        logins.user("sync-second@example.com");

        restartWith("Sync-First@Example.com");

        assertThat(roleOf("sync-first@example.com")).isEqualTo(Role.ANALYST);
        assertThat(roleOf("sync-second@example.com")).isEqualTo(Role.PORTFOLIO_MANAGER);

        restartWith("sync-second@example.com");

        assertThat(roleOf("sync-first@example.com")).isEqualTo(Role.PORTFOLIO_MANAGER);
        assertThat(roleOf("sync-second@example.com")).isEqualTo(Role.ANALYST);
    }

    @Test
    void aRoleGivenAnyOtherWayDoesNotSurviveARestartThatDoesNotListTheEmail() {
        logins.analystBearer("sync-unlisted@example.com");

        restartWith("someone-else@example.com");

        assertThat(roleOf("sync-unlisted@example.com")).isEqualTo(Role.PORTFOLIO_MANAGER);
    }

    @Test
    void theLocalDemoAnalystKeepsItsRoleEvenThoughItIsNotInTheSetting() {
        restartWith("");

        assertThat(roleOf(DemoUserSeeder.ANALYST_EMAIL)).isEqualTo(Role.ANALYST);
    }
}
