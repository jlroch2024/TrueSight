package com.truesight.user;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Creates a demo account when TrueSight starts on a laptop or in tests, so anybody can log in straight away:
 * email {@value #DEMO_EMAIL}, password {@value #DEMO_PASSWORD}. On a laptop it also owns the Demo Portfolio.
 *
 * <p>{@code @Profile} is what keeps it off the live site: this class only exists when the app runs with the "local"
 * profile (the default on a laptop) or the "test" profile (the automatic tests). The live site runs with "prod", so
 * this account, and its well-known password, never reach the real database.
 */
@Component
@Profile({"local", "test"})
@Order(1)
public class DemoUserSeeder implements ApplicationRunner {

    public static final String DEMO_EMAIL = "demo@truesight.local";
    public static final String DEMO_PASSWORD = "truesight-demo";

    private final UserRepository users;
    private final PasswordEncoder passwords;

    public DemoUserSeeder(UserRepository users, PasswordEncoder passwords) {
        this.users = users;
        this.passwords = passwords;
    }

    @Override
    public void run(ApplicationArguments args) {
        User demo = users.findByEmail(DEMO_EMAIL).orElseGet(() -> new User(DEMO_EMAIL, null));
        if (demo.getPasswordHash() == null) {
            demo.setPasswordHash(passwords.encode(DEMO_PASSWORD));
            users.save(demo);
        }
    }
}
