package com.truesight.portfolio;

import com.truesight.user.DemoUserSeeder;
import com.truesight.user.UserRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Gives the demo user one empty portfolio, "Demo Portfolio", when TrueSight runs on a laptop.
 *
 * <p>Why: the upload, report and graph stories all need a portfolio to work on, but only the Manage My Portfolios
 * story can create one. With this, anybody can open http://localhost:5173/portfolios/1 and build their page before
 * that story is merged.
 *
 * <p>Only the "local" profile runs it: not the tests (each test creates the data it needs) and never the live site.
 * It runs after {@link DemoUserSeeder}, which creates the demo user.
 */
@Component
@Profile("local")
@Order(2)
public class DemoPortfolioSeeder implements ApplicationRunner {

    public static final String DEMO_PORTFOLIO = "Demo Portfolio";

    private final UserRepository users;
    private final PortfolioRepository portfolios;

    public DemoPortfolioSeeder(UserRepository users, PortfolioRepository portfolios) {
        this.users = users;
        this.portfolios = portfolios;
    }

    @Override
    public void run(ApplicationArguments args) {
        users.findByEmail(DemoUserSeeder.DEMO_EMAIL).ifPresent(demo -> {
            if (!portfolios.existsByUserIdAndName(demo.getId(), DEMO_PORTFOLIO)) {
                portfolios.save(new Portfolio(demo.getId(), DEMO_PORTFOLIO));
            }
        });
    }
}
