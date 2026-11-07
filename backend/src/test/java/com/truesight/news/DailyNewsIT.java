package com.truesight.news;

import com.truesight.company.Company;
import com.truesight.company.CompanyDirectory;
import com.truesight.company.CompanyRepository;
import com.truesight.portfolio.Holding;
import com.truesight.portfolio.HoldingRepository;
import com.truesight.portfolio.HoldingStatus;
import com.truesight.portfolio.NewsStatus;
import com.truesight.portfolio.Portfolio;
import com.truesight.portfolio.PortfolioRepository;
import com.truesight.report.SecClient;
import com.truesight.support.Examples;
import com.truesight.support.IntegrationTest;
import com.truesight.support.TestLogins;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
@TestPropertySource(properties = "truesight.news.scheduler-token=daily-test-token")
class DailyNewsIT {

    static final String TOKEN = "daily-test-token";

    @Autowired MockMvc mockMvc;
    @Autowired TestLogins logins;
    @Autowired PortfolioRepository portfolios;
    @Autowired HoldingRepository holdings;
    @Autowired CompanyDirectory companyDirectory;
    @Autowired CompanyRepository companies;
    @Autowired NewsArticleRepository articles;

    @MockitoBean NewsClient news;
    // A news check also reads each company's 8-Ks: never from the real SEC in a test.
    @MockitoBean SecClient sec;
    @MockitoBean NewsCheckStepRunner steps;

    @BeforeEach
    void returnTheSavedGoogleNewsSample() {
        reset(news, steps);
        List<NewsItem> parsed = new GoogleNewsParser().parse(Examples.googleNewsTsmc());
        Instant newest = parsed.stream().map(NewsItem::publishedAt).max(Instant::compareTo).orElseThrow();
        Duration shift = Duration.between(newest, Instant.now().minus(Duration.ofHours(1)));
        when(news.search(anyString())).thenReturn(parsed.stream()
                .map(item -> new NewsItem(item.title(), item.url(), item.source(), item.publishedAt().plus(shift)))
                .toList());
    }

    @Test
    void aRequestWithTheRightTokenChecksEveryPortfolioWithAnAnalysedHoldingAndAnswers202() throws Exception {
        Portfolio first = analysedPortfolio("daily-one@example.com", "Daily One Corporation", "DAYONE");
        Portfolio second = analysedPortfolio("daily-two@example.com", "Daily Two Corporation", "DAYTWO");
        Portfolio unanalysed = portfolios.save(new Portfolio(logins.user("daily-none@example.com").getId(), "Empty"));

        mockMvc.perform(post("/api/news-checks/daily").header("X-Scheduler-Token", TOKEN))
                .andExpect(status().isAccepted());
        awaitChecked(first.getId());
        awaitChecked(second.getId());

        verify(news).search("Daily One");
        verify(news).search("Daily Two");
        assertThat(portfolios.findById(unanalysed.getId()).orElseThrow().getNewsCheckedAt()).isNull();
    }

    @Test
    void aWrongOrMissingTokenIsRefusedWith401AndNothingRuns() throws Exception {
        Portfolio portfolio = analysedPortfolio("daily-refused@example.com", "Daily Refused Corporation", "DAYREF");

        mockMvc.perform(post("/api/news-checks/daily").header("X-Scheduler-Token", "wrong"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").isNotEmpty());
        mockMvc.perform(post("/api/news-checks/daily")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/news-checks/daily").header("X-Scheduler-Token", "")).andExpect(status().isUnauthorized());
        Thread.sleep(300);

        verify(news, never()).search("Daily Refused");
        assertThat(portfolios.findById(portfolio.getId()).orElseThrow().getNewsCheckedAt()).isNull();
    }

    @Test
    void eachPortfoliosNewsCheckedTimeAndStepsUpdateAsAfterCheckNews() throws Exception {
        Portfolio portfolio = analysedPortfolio("daily-after@example.com", "Daily After Corporation", "DAYAFT");

        mockMvc.perform(post("/api/news-checks/daily").header("X-Scheduler-Token", TOKEN))
                .andExpect(status().isAccepted());
        awaitChecked(portfolio.getId());

        Portfolio checked = portfolios.findById(portfolio.getId()).orElseThrow();
        assertThat(checked.getNewsStatus()).isEqualTo(NewsStatus.IDLE);
        assertThat(checked.getNewsStatusReason()).isNull();
        verify(steps).run(portfolio.getId());
    }

    private void awaitChecked(Long portfolioId) throws Exception {
        for (int attempt = 0; attempt < 400; attempt++) {
            Portfolio portfolio = portfolios.findById(portfolioId).orElseThrow();
            if (portfolio.getNewsCheckedAt() != null && portfolio.getNewsStatus() == NewsStatus.IDLE) return;
            Thread.sleep(25);
        }
        assertThat(portfolios.findById(portfolioId).orElseThrow().getNewsCheckedAt()).isNotNull();
    }

    private Portfolio analysedPortfolio(String email, String companyName, String ticker) {
        Company company = companyDirectory.findOrCreate(companyName, null, ticker, List.of());
        articles.deleteByCompanyId(company.getId());
        company.setNewsCheckedAt(null);
        companies.saveAndFlush(company);
        Portfolio portfolio = portfolios.save(new Portfolio(logins.user(email).getId(), "Daily Test"));
        Holding holding = new Holding(portfolio.getId(), ticker, new BigDecimal("100"));
        holding.setCompanyId(company.getId());
        holding.setStatus(HoldingStatus.DONE);
        holdings.save(holding);
        return portfolio;
    }
}
