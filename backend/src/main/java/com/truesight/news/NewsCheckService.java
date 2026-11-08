package com.truesight.news;

import com.truesight.common.ApiException;
import com.truesight.company.Company;
import com.truesight.company.CompanyRepository;
import com.truesight.config.WorkRecovery;
import com.truesight.config.WorkerHeartbeat;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Owns the database state for starting, saving and finishing a portfolio news check. */
@Service
public class NewsCheckService {

    static final String ALREADY_CHECKING = "News is already being checked for this portfolio.";
    static final String ANALYSE_FIRST = "Analyse the portfolio before checking the news.";
    private static final String NOT_FOUND = "Portfolio not found.";
    /** Why a news check is Failed when its server stopped before the check ended. */
    public static final String INTERRUPTED_REASON = "The news check was interrupted. Check News to try again.";

    private final PortfolioRepository portfolios;
    private final HoldingRepository holdings;
    private final RelationshipRepository relationships;
    private final CompanyRepository companies;
    private final NewsArticleRepository articles;
    private final CurrentUser currentUser;
    private final WorkRecovery recovery;
    private final WorkerHeartbeat heartbeat;

    public NewsCheckService(PortfolioRepository portfolios, HoldingRepository holdings,
                            RelationshipRepository relationships, CompanyRepository companies,
                            NewsArticleRepository articles, CurrentUser currentUser,
                            WorkRecovery recovery, WorkerHeartbeat heartbeat) {
        this.portfolios = portfolios;
        this.holdings = holdings;
        this.relationships = relationships;
        this.companies = companies;
        this.articles = articles;
        this.currentUser = currentUser;
        this.recovery = recovery;
        this.heartbeat = heartbeat;
    }

    @Transactional
    public void start(Long portfolioId) {
        // First, and before the row lock below: it commits on its own, so a check whose server has gone is Failed
        // before this one is refused for it.
        recovery.recover(portfolioId);
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
        portfolio.setNewsWorkerId(heartbeat.id());
    }

    /** The portfolios the daily check covers: every one with at least one analysed holding. */
    @Transactional(readOnly = true)
    public List<Long> portfolioIdsWithAnalysedHoldings() {
        return holdings.findPortfolioIdsWithStatus(HoldingStatus.DONE);
    }

    /** Like {@link #start}, for the daily check, which has no logged-in user. False if it should be skipped. */
    @Transactional
    public boolean startForDaily(Long portfolioId) {
        recovery.recover(portfolioId);
        Portfolio portfolio = portfolios.findById(portfolioId).orElse(null);
        if (portfolio == null || portfolio.getNewsStatus() == NewsStatus.CHECKING) return false;
        portfolio.setNewsStatus(NewsStatus.CHECKING);
        portfolio.setNewsStatusReason(null);
        portfolio.setNewsWorkerId(heartbeat.id());
        return true;
    }

    /** Every company this portfolio's news is about, the three rounds together. */
    @Transactional(readOnly = true)
    public List<NewsCompany> companiesFor(Long portfolioId) {
        return roundsFor(portfolioId).all();
    }

    /**
     * The companies this portfolio's news is about, in the three rounds a news check fetches them in, most important
     * first: the holdings themselves, then their suppliers and the companies they name, then those suppliers'
     * suppliers. Each company is in the first round it belongs to; which companies are checked at all is the same as
     * if the rounds were one list.
     */
    @Transactional(readOnly = true)
    public NewsRounds roundsFor(Long portfolioId) {
        Set<Long> heldCompanies = new LinkedHashSet<>();
        holdings.findByPortfolioIdAndStatusOrderByTickerAsc(portfolioId, HoldingStatus.DONE).stream()
                .map(Holding::getCompanyId)
                .filter(id -> id != null)
                .forEach(heldCompanies::add);
        // The companies the holdings' own reports name, and the holdings' direct suppliers.
        Set<Long> suppliers = new LinkedHashSet<>();
        relationships.findByCompanyIdIn(heldCompanies).stream()
                .filter(relationship -> relationship.getReviewStatus() != ReviewStatus.REJECTED)
                .map(Relationship::getCounterpartyId)
                .forEach(suppliers::add);
        Set<Long> directSuppliers = suppliersOf(heldCompanies);
        suppliers.addAll(directSuppliers);
        Set<Long> suppliersSuppliers = suppliersOf(directSuppliers);
        // Each company is in the first round it belongs to.
        suppliers.removeAll(heldCompanies);
        suppliersSuppliers.removeAll(heldCompanies);
        suppliersSuppliers.removeAll(suppliers);
        return new NewsRounds(named(heldCompanies), named(suppliers), named(suppliersSuppliers));
    }

    /** The companies that supply any of these customers, by the relationships that are not Rejected. */
    private Set<Long> suppliersOf(Set<Long> customers) {
        return relationships.findByCompanyIdInOrCounterpartyIdIn(customers, customers).stream()
                .filter(relationship -> relationship.getReviewStatus() != ReviewStatus.REJECTED)
                .filter(relationship -> customers.contains(customerId(relationship)))
                .map(NewsCheckService::supplierId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private List<NewsCompany> named(Set<Long> companyIds) {
        return companies.findAllById(companyIds).stream()
                .map(company -> new NewsCompany(company.getId(), company.getName(), company.getCik()))
                .toList();
    }

    /** The company that supplies the other in this relationship, whichever company's report it came from. */
    public static Long supplierId(Relationship relationship) {
        return relationship.getType() == RelationshipType.SUPPLIER
                ? relationship.getCounterpartyId() : relationship.getCompanyId();
    }

    /** The company that buys from the other in this relationship, whichever company's report it came from. */
    public static Long customerId(Relationship relationship) {
        return relationship.getType() == RelationshipType.SUPPLIER
                ? relationship.getCompanyId() : relationship.getCounterpartyId();
    }

    /** True when the company's news was fetched less than {@code window} before {@code now}. */
    @Transactional(readOnly = true)
    public boolean wasCheckedWithin(Long companyId, Instant now, Duration window) {
        return companies.findById(companyId)
                .map(Company::getNewsCheckedAt)
                .filter(checkedAt -> checkedAt.isAfter(now.minus(window)))
                .isPresent();
    }

    /** Whether an article with this address is already saved for the company. */
    @Transactional(readOnly = true)
    public boolean isSaved(Long companyId, String url) {
        return articles.existsByCompanyIdAndUrl(companyId, url);
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

    /** A company a news check covers. cik is its SEC number, or null when it has none. */
    public record NewsCompany(Long id, String name, String cik) {
        /** Whether the SEC has a number for it, so its 8-Ks can be read. */
        public boolean hasSecNumber() {
            return cik != null && !cik.isBlank();
        }
    }

    /**
     * The companies of a news check in the order they are fetched: the holdings, their suppliers (and the companies
     * they name), and those suppliers' suppliers, which are asked less often.
     */
    public record NewsRounds(List<NewsCompany> holdings, List<NewsCompany> suppliers,
                             List<NewsCompany> suppliersSuppliers) {

        /** All three rounds as one list. */
        public List<NewsCompany> all() {
            return Stream.of(holdings, suppliers, suppliersSuppliers).flatMap(List::stream).toList();
        }
    }
}
