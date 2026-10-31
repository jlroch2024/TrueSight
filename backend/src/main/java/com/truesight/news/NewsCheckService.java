package com.truesight.news;

import com.truesight.common.ApiException;
import com.truesight.company.Company;
import com.truesight.company.CompanyRepository;
import com.truesight.portfolio.Holding;
import com.truesight.portfolio.HoldingRepository;
import com.truesight.portfolio.HoldingStatus;
import com.truesight.portfolio.NewsStatus;
import com.truesight.portfolio.Portfolio;
import com.truesight.portfolio.PortfolioRepository;
import com.truesight.relationship.Relationship;
import com.truesight.relationship.RelationshipRepository;
import com.truesight.relationship.RelationshipType;
import com.truesight.relationship.ReviewStatus;
import com.truesight.user.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Owns the database state for starting, saving and finishing a portfolio news check. */
@Service
public class NewsCheckService {

    static final String ALREADY_CHECKING = "News is already being checked for this portfolio.";
    static final String ANALYSE_FIRST = "Analyse the portfolio before checking the news.";
    private static final String NOT_FOUND = "Portfolio not found.";

    private final PortfolioRepository portfolios;
    private final HoldingRepository holdings;
    private final RelationshipRepository relationships;
    private final CompanyRepository companies;
    private final NewsArticleRepository articles;
    private final CurrentUser currentUser;

    public NewsCheckService(PortfolioRepository portfolios, HoldingRepository holdings,
                            RelationshipRepository relationships, CompanyRepository companies,
                            NewsArticleRepository articles, CurrentUser currentUser) {
        this.portfolios = portfolios;
        this.holdings = holdings;
        this.relationships = relationships;
        this.companies = companies;
        this.articles = articles;
        this.currentUser = currentUser;
    }

    @Transactional
    public void start(Long portfolioId) {
        Portfolio portfolio = portfolios.findForUpdateByIdAndUserId(portfolioId, currentUser.id())
                .orElseThrow(() -> ApiException.notFound(NOT_FOUND));
        if (portfolio.getNewsStatus() == NewsStatus.CHECKING) {
            throw ApiException.conflict(ALREADY_CHECKING);
        }
        boolean hasAnalysedHolding = holdings
                .findByPortfolioIdAndStatusOrderByTickerAsc(portfolioId, HoldingStatus.DONE)
                .stream().anyMatch(holding -> holding.getCompanyId() != null);
        if (!hasAnalysedHolding) {
            throw ApiException.conflict(ANALYSE_FIRST);
        }
        portfolio.setNewsStatus(NewsStatus.CHECKING);
        portfolio.setNewsStatusReason(null);
    }

    /** The portfolios the daily check covers: every one with at least one analysed holding. */
    @Transactional(readOnly = true)
    public List<Long> portfolioIdsWithAnalysedHoldings() {
        return holdings.findPortfolioIdsWithStatus(HoldingStatus.DONE);
    }

    /** Like {@link #start}, for the daily check, which has no logged-in user. False if it should be skipped. */
    @Transactional
    public boolean startForDaily(Long portfolioId) {
        Portfolio portfolio = portfolios.findById(portfolioId).orElse(null);
        if (portfolio == null || portfolio.getNewsStatus() == NewsStatus.CHECKING) return false;
        portfolio.setNewsStatus(NewsStatus.CHECKING);
        portfolio.setNewsStatusReason(null);
        return true;
    }

    /** Every company this portfolio's news covers, in no particular order: the three rounds of {@link #roundsFor}. */
    @Transactional(readOnly = true)
    public List<NewsCompany> companiesFor(Long portfolioId) {
        return roundsFor(portfolioId).all();
    }

    /**
     * The companies this portfolio's news covers, in the three rounds a news check works through, most important
     * first: the holdings themselves; then the companies one step from a holding (its direct suppliers, and the
     * counterparties its relationships name); then the suppliers of those direct suppliers. A company is in only
     * the first round that has it, so a company in the third round is only a supplier's supplier in this portfolio.
     */
    @Transactional(readOnly = true)
    public NewsRounds roundsFor(Long portfolioId) {
        Set<Long> heldCompanies = new LinkedHashSet<>();
        holdings.findByPortfolioIdAndStatusOrderByTickerAsc(portfolioId, HoldingStatus.DONE).stream()
                .map(Holding::getCompanyId)
                .filter(id -> id != null)
                .forEach(heldCompanies::add);
        if (heldCompanies.isEmpty()) return new NewsRounds(List.of(), List.of(), List.of());

        Set<Long> nextToHoldings = new LinkedHashSet<>();
        relationships.findByCompanyIdIn(heldCompanies).stream()
                .filter(relationship -> relationship.getReviewStatus() != ReviewStatus.REJECTED)
                .map(Relationship::getCounterpartyId)
                .forEach(nextToHoldings::add);
        Set<Long> directSuppliers = relationships
                .findByCompanyIdInOrCounterpartyIdIn(heldCompanies, heldCompanies).stream()
                .filter(relationship -> relationship.getReviewStatus() != ReviewStatus.REJECTED)
                .filter(relationship -> heldCompanies.contains(customerId(relationship)))
                .map(NewsCheckService::supplierId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        nextToHoldings.addAll(directSuppliers);
        Set<Long> suppliersSuppliers = new LinkedHashSet<>();
        relationships.findByCompanyIdInOrCounterpartyIdIn(directSuppliers, directSuppliers).stream()
                .filter(relationship -> relationship.getReviewStatus() != ReviewStatus.REJECTED)
                .filter(relationship -> directSuppliers.contains(customerId(relationship)))
                .map(NewsCheckService::supplierId)
                .forEach(suppliersSuppliers::add);

        nextToHoldings.removeAll(heldCompanies);
        suppliersSuppliers.removeAll(heldCompanies);
        suppliersSuppliers.removeAll(nextToHoldings);

        Set<Long> everyone = new LinkedHashSet<>(heldCompanies);
        everyone.addAll(nextToHoldings);
        everyone.addAll(suppliersSuppliers);
        Map<Long, NewsCompany> byId = new HashMap<>();
        companies.findAllById(everyone)
                .forEach(company -> byId.put(company.getId(), new NewsCompany(company.getId(), company.getName())));
        return new NewsRounds(round(heldCompanies, byId), round(nextToHoldings, byId), round(suppliersSuppliers, byId));
    }

    private static List<NewsCompany> round(Set<Long> ids, Map<Long, NewsCompany> byId) {
        return ids.stream().map(byId::get).filter(company -> company != null).toList();
    }

    private static Long supplierId(Relationship relationship) {
        return relationship.getType() == RelationshipType.SUPPLIER
                ? relationship.getCounterpartyId() : relationship.getCompanyId();
    }

    private static Long customerId(Relationship relationship) {
        return relationship.getType() == RelationshipType.SUPPLIER
                ? relationship.getCompanyId() : relationship.getCounterpartyId();
    }

    /** Whether the company's news was fetched less than {@code window} before {@code now}. */
    @Transactional(readOnly = true)
    public boolean wasCheckedWithin(Long companyId, Instant now, Duration window) {
        return companies.findById(companyId)
                .map(Company::getNewsCheckedAt)
                .filter(checkedAt -> checkedAt.isAfter(now.minus(window)))
                .isPresent();
    }

    @Transactional
    public void save(Long companyId, List<NewsItem> items, Instant checkedAt) {
        Company company = companies.findById(companyId).orElse(null);
        if (company == null) return;
        for (NewsItem item : items) {
            if (!articles.existsByCompanyIdAndUrl(companyId, item.url())) {
                articles.save(new NewsArticle(companyId, item.url(), item.title(), item.source(), item.publishedAt()));
            }
        }
        company.setNewsCheckedAt(checkedAt);
    }

    @Transactional
    public void finish(Long portfolioId, Instant checkedAt, String reason) {
        portfolios.findById(portfolioId).ifPresent(portfolio -> {
            portfolio.setNewsStatus(NewsStatus.IDLE);
            portfolio.setNewsStatusReason(reason);
            portfolio.setNewsCheckedAt(checkedAt);
        });
    }

    @Transactional
    public void fail(Long portfolioId, String reason) {
        portfolios.findById(portfolioId).ifPresent(portfolio -> {
            portfolio.setNewsStatus(NewsStatus.FAILED);
            portfolio.setNewsStatusReason(reason);
        });
    }

    public record NewsCompany(Long id, String name) {
    }

    /**
     * The three rounds of a news check, most important first: the holdings, then their direct suppliers (and the
     * counterparties their relationships name), then the suppliers' suppliers.
     */
    public record NewsRounds(List<NewsCompany> holdings, List<NewsCompany> suppliers,
                             List<NewsCompany> suppliersSuppliers) {

        /** Every company of every round. */
        public List<NewsCompany> all() {
            return Stream.of(holdings, suppliers, suppliersSuppliers).flatMap(List::stream).toList();
        }
    }
}
