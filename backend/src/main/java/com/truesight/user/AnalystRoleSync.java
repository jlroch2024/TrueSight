package com.truesight.user;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Every time TrueSight starts, makes each user's role match {@link AnalystEmails}: a listed email becomes an Analyst,
 * and anybody else a Portfolio Manager. So adding an existing user's email to the setting promotes them on the next
 * start, and removing it demotes them again.
 *
 * <p>It runs in every profile, after {@link DemoUserSeeder} ({@code @Order(1)}). The local demo analyst is skipped: it
 * is not in the setting, but it keeps its role.
 */
@Component
@Order(2)
public class AnalystRoleSync implements ApplicationRunner {

    private final UserRepository users;
    private final AnalystEmails analystEmails;

    public AnalystRoleSync(UserRepository users, AnalystEmails analystEmails) {
        this.users = users;
        this.analystEmails = analystEmails;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        for (User user : users.findAll()) {
            if (DemoUserSeeder.ANALYST_EMAIL.equals(user.getEmail())) {
                continue;
            }
            Role wanted = analystEmails.isAnalyst(user.getEmail()) ? Role.ANALYST : Role.PORTFOLIO_MANAGER;
            if (user.getRole() != wanted) {
                user.setRole(wanted);
                users.save(user);
            }
        }
    }
}
