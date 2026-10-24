package com.truesight.news;

import com.truesight.common.ApiException;
import com.truesight.company.Company;
import com.truesight.company.CompanyDisplayNames;
import com.truesight.company.CompanyRepository;
import com.truesight.portfolio.Holding;
import com.truesight.portfolio.HoldingRepository;
import com.truesight.portfolio.HoldingStatus;
import com.truesight.portfolio.PortfolioRepository;
import com.truesight.user.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The recent news about a portfolio's supply chain: the articles saved by news checks, newest first, for the same
 * companies a news check asks about (its analysed holdings, and their suppliers and customers that are not Rejected).
 */
@Service
public class NewsListService {

    /** How far back the list goes: the same 7 days a news check collects. */
    static final Duration RECENT = Duration.ofDays(7);
    private static final String NOT_FOUND = "Portfolio not found.";

    private final PortfolioRepository portfolios;
    private final HoldingRepository holdings;
    private final CompanyRepository companies;
    private final CompanyDisplayNames displayNames;
    private final NewsArticleRepository articles;
    private final NewsAssessmentRepository assessments;
    private final NewsCheckService newsCheck;
    private final CurrentUser currentUser;

    public NewsListService(PortfolioRepository portfolios, HoldingRepository holdings, CompanyRepository companies,
                           CompanyDisplayNames displayNames, NewsArticleRepository articles, NewsAssessmentRepository assessments,
                           NewsCheckService newsCheck, CurrentUser currentUser) {
        this.portfolios = portfolios;
        this.holdings = holdings;
        this.companies = companies;
        this.displayNames = displayNames;
        this.articles = articles;
        this.assessments = assessments;
        this.newsCheck = newsCheck;
        this.currentUser = currentUser;
    }

    @Transactional(readOnly = true)
    public List<NewsArticleResponse> list(Long portfolioId) {
        return list(portfolioId, false);
    }

    @Transactional(readOnly = true)
    public List<NewsArticleResponse> list(Long portfolioId, boolean noiseOnly) {
        NewsSelection selection = selection(portfolioId);
        return selection.articles().stream()
                .filter(article -> !noiseOnly || isNoise(selection.assessmentByArticle().get(article.getId())))
                .map(article -> NewsArticleResponse.from(article,
                        selection.companyById().get(article.getCompanyId()),
                        displayNames.of(selection.companyById().get(article.getCompanyId())),
                        selection.heldCompanyIds().contains(article.getCompanyId()),
                        selection.assessmentByArticle().get(article.getId())))
                .toList();
    }

    private NewsSelection selection(Long portfolioId) {
        portfolios.findByIdAndUserId(portfolioId, currentUser.id())
                .orElseThrow(() -> ApiException.notFound(NOT_FOUND));

        List<Long> companyIds = newsCheck.companiesFor(portfolioId).stream()
                .map(NewsCheckService.NewsCompany::id)
                .toList();
        if (companyIds.isEmpty()) {
            return new NewsSelection(List.of(), Map.of(), Set.of(), Map.of());
        }

        List<NewsArticle> recent = articles.findTop200ByCompanyIdInAndPublishedAtAfterOrderByPublishedAtDesc(
                companyIds, Instant.now().minus(RECENT));
        Map<Long, Company> companyById = companies.findAllById(companyIds).stream()
                .collect(Collectors.toMap(Company::getId, Function.identity()));
        Set<Long> heldCompanyIds = holdings.findByPortfolioIdAndStatusOrderByTickerAsc(portfolioId, HoldingStatus.DONE)
                .stream()
                .map(Holding::getCompanyId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, NewsAssessment> assessmentByArticle = assessments
                .findByArticleIdIn(recent.stream().map(NewsArticle::getId).toList()).stream()
                .collect(Collectors.toMap(NewsAssessment::getArticleId, Function.identity()));

        return new NewsSelection(recent, companyById, heldCompanyIds, assessmentByArticle);
    }

    private boolean isNoise(NewsAssessment assessment) {
        return assessment != null && !assessment.isEvent();
    }

    private record NewsSelection(
            List<NewsArticle> articles,
            Map<Long, Company> companyById,
            Set<Long> heldCompanyIds,
            Map<Long, NewsAssessment> assessmentByArticle) {
    }
}
