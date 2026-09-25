package com.truesight.user;

import com.truesight.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the placeholder works in tests: the demo user exists, and {@link CurrentUser} answers with it.
 *
 * <p>The Sign Up and Log In story replaces {@link CurrentUser} with the real logged-in user, and rewrites this test.
 */
@IntegrationTest
class CurrentUserIT {

    @Autowired
    CurrentUser currentUser;

    @Autowired
    UserRepository users;

    @Test
    void theCurrentUserIsTheDemoUserUntilRealLoginsExist() {
        User demo = users.findByEmail(DemoUserSeeder.DEMO_EMAIL).orElseThrow();

        assertThat(currentUser.id()).isEqualTo(demo.getId());
    }
}
