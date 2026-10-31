package com.truesight.news;

import com.truesight.company.Company;
import com.truesight.company.CompanyDirectory;
import com.truesight.portfolio.Holding;
import com.truesight.portfolio.HoldingRepository;
import com.truesight.portfolio.HoldingStatus;
import com.truesight.portfolio.Portfolio;
import com.truesight.portfolio.PortfolioRepository;
import com.truesight.support.IntegrationTest;
import com.truesight.support.TestLogins;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The part of the daily news check's tests that needs {@code truesight.news.daily-enabled} off. */
@IntegrationTest
@TestPropertySource(properties = {"truesight.news.scheduler-token=daily-test-token",
        "truesight.news.daily-enabled=false"})
class DailyNewsDisabledIT {

    @Autowired MockMvc mockMvc;
    @Autowired TestLogins logins;
    @Autowired PortfolioRepository portfolios;
    @Autowired HoldingRepository holdings;
    @Autowired CompanyDirectory companyDirectory;

    @MockitoBean NewsClient news;
    @MockitoBean NewsCheckStepRunner steps;

    @Test
    void withTheSettingOffTheEndpointAnswers202AndChecksNothing() throws Exception {
        Company company = companyDirectory.findOrCreate("Daily Off Corporation", null, "DAYOFF", List.of());
        Portfolio portfolio = portfolios.save(new Portfolio(logins.user("daily-off@example.com").getId(), "Off"));
        Holding holding = new Holding(portfolio.getId(), "DAYOFF", new BigDecimal("100"));
        holding.setCompanyId(company.getId());
        holding.setStatus(HoldingStatus.DONE);
        holdings.save(holding);

        mockMvc.perform(post("/api/news-checks/daily").header("X-Scheduler-Token", "daily-test-token"))
                .andExpect(status().isAccepted());
        Thread.sleep(500);

        verify(news, never()).search(anyString());
        assertThat(portfolios.findById(portfolio.getId()).orElseThrow().getNewsCheckedAt()).isNull();
    }
}
