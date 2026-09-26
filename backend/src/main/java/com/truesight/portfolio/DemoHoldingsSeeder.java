package com.truesight.portfolio;

import com.truesight.user.DemoUserSeeder;
import com.truesight.user.UserRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Loads the repository's sample CSV into the local demo portfolio so the report story has holdings to analyse. */
@Component
@Profile("local")
@Order(3)
public class DemoHoldingsSeeder implements ApplicationRunner {

    private final UserRepository users;
    private final PortfolioRepository portfolios;
    private final HoldingRepository holdings;

    public DemoHoldingsSeeder(UserRepository users, PortfolioRepository portfolios, HoldingRepository holdings) {
        this.users = users;
        this.portfolios = portfolios;
        this.holdings = holdings;
    }

    @Override
    public void run(ApplicationArguments args) {
        users.findByEmail(DemoUserSeeder.DEMO_EMAIL).flatMap(user ->
                portfolios.findByUserIdOrderByNameAsc(user.getId()).stream()
                        .filter(portfolio -> portfolio.getName().equals(DemoPortfolioSeeder.DEMO_PORTFOLIO)).findFirst()
        ).ifPresent(portfolio -> {
            if (holdings.countByPortfolioId(portfolio.getId()) != 0) return;
            Path csv = Files.exists(Path.of("docs/examples/holdings.csv"))
                    ? Path.of("docs/examples/holdings.csv") : Path.of("../docs/examples/holdings.csv");
            try {
                List<Holding> sample = Files.readAllLines(csv).stream().skip(1).filter(line -> !line.isBlank()).map(line -> {
                    String[] fields = line.split(",", -1);
                    BigDecimal weight = fields.length > 1 && !fields[1].isBlank() ? new BigDecimal(fields[1]) : null;
                    return new Holding(portfolio.getId(), fields[0].trim(), weight);
                }).toList();
                holdings.saveAll(sample);
            } catch (IOException | NumberFormatException exception) {
                throw new IllegalStateException("The sample holdings CSV could not be loaded.", exception);
            }
        });
    }
}
