package com.truesight.user;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Creates the demo user when TrueSight starts on a laptop or in tests, if it is not there already.
 *
 * <p>{@code @Profile} is what keeps it off the live site: this class only exists when the app runs with the "local"
 * profile (the default on a laptop) or the "test" profile (the automatic tests). The live site runs with "prod", so
 * the demo user never reaches the real database.
 *
 * <p>The demo user has no password, so nobody can log in as it. It exists only for {@link CurrentUser} to point at
 * until real logins arrive.
 */
@Component
@Profile({"local", "test"})
public class DemoUserSeeder implements ApplicationRunner {

    public static final String DEMO_EMAIL = "demo@truesight.local";

    private final UserRepository users;

    public DemoUserSeeder(UserRepository users) {
        this.users = users;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!users.existsByEmail(DEMO_EMAIL)) {
            users.save(new User(DEMO_EMAIL, null));
        }
    }
}
