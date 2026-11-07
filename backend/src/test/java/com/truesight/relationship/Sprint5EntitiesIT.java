package com.truesight.relationship;

import com.truesight.ai.AiCall;
import com.truesight.ai.AiCallRepository;
import com.truesight.ai.AiOutcome;
import com.truesight.ai.AiPurpose;
import com.truesight.company.Company;
import com.truesight.company.CompanyRepository;
import com.truesight.config.Worker;
import com.truesight.config.WorkerRepository;
import com.truesight.portfolio.Holding;
import com.truesight.portfolio.HoldingRepository;
import com.truesight.portfolio.Portfolio;
import com.truesight.portfolio.PortfolioRepository;
import com.truesight.report.Concentration;
import com.truesight.report.ConcentrationKind;
import com.truesight.report.ConcentrationMeasure;
import com.truesight.report.ConcentrationRepository;
import com.truesight.report.Report;
import com.truesight.report.ReportRepository;
import com.truesight.support.IntegrationTest;
import com.truesight.support.TestLogins;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The Sprint 5 table changes against a real database, through the entities and repositories (the app starts with
 * {@code ddl-auto: validate}, so each entity must match its table).
 */
@IntegrationTest
class Sprint5EntitiesIT {

    private static final String EVIDENCE = "We rely on Example Supplier for all of our wafers.";

    @Autowired CompanyRepository companies;
    @Autowired ReportRepository reports;
    @Autowired RelationshipRepository relationships;
    @Autowired ConcentrationRepository concentrations;
    @Autowired NameSearchRepository nameSearches;
    @Autowired WorkerRepository workers;
    @Autowired PortfolioRepository portfolios;
    @Autowired HoldingRepository holdings;
    @Autowired AiCallRepository aiCalls;
    @Autowired TestLogins logins;

    private Company company(String name) {
        return companies.save(new Company(name + " " + UUID.randomUUID(), null, null));
    }

    private Report report(Company company) {
        return reports.save(new Report(company.getId(), "10-K", "s5-" + UUID.randomUUID().toString().substring(0, 20),
                LocalDate.of(2025, 2, 1), "https://example.com/report", "report text"));
    }

    private Portfolio portfolio() {
        Long userId = logins.user("s5-" + UUID.randomUUID() + "@example.com").getId();
        return portfolios.save(new Portfolio(userId, "Sprint 5 " + UUID.randomUUID()));
    }

    private Concentration figure(Report report, Company company, Long counterpartyId, BigDecimal share) {
        return new Concentration(report.getId(), company.getId(), counterpartyId, "Apple", ConcentrationKind.CUSTOMER,
                ConcentrationMeasure.REVENUE, share, LocalDate.of(2024, 9, 29), LocalDate.of(2025, 9, 27));
    }

    @Test
    void aDependencyFigureIsSavedAndReadBackByItsCompanyAndByItsCounterparty() {
        Company supplier = company("Supplier");
        Company customer = company("Customer");
        Report report = report(supplier);

        Concentration saved = concentrations.save(figure(report, supplier, customer.getId(), new BigDecimal("0.9100")));

        Concentration found = concentrations.findByCompanyIdIn(List.of(supplier.getId())).get(0);
        assertThat(found.getId()).isEqualTo(saved.getId());
        assertThat(found.getReportId()).isEqualTo(report.getId());
        assertThat(found.getCounterpartyId()).isEqualTo(customer.getId());
        assertThat(found.getLabel()).isEqualTo("Apple");
        assertThat(found.getKind()).isEqualTo(ConcentrationKind.CUSTOMER);
        assertThat(found.getMeasure()).isEqualTo(ConcentrationMeasure.REVENUE);
        assertThat(found.getShare()).isEqualByComparingTo("0.91");
        assertThat(found.getPeriodStart()).isEqualTo(LocalDate.of(2024, 9, 29));
        assertThat(found.getPeriodEnd()).isEqualTo(LocalDate.of(2025, 9, 27));
        assertThat(found.getCreatedAt()).isNotNull();
        assertThat(concentrations.findByCounterpartyIdIn(List.of(customer.getId()))).hasSize(1);
        assertThat(concentrations.findByReportIdOrderByIdAsc(report.getId())).hasSize(1);
    }

    @Test
    void aDependencyFigureMayNameNoCompany() {
        Company supplier = company("Supplier");

        Concentration saved = concentrations.save(figure(report(supplier), supplier, null, new BigDecimal("0.2300")));

        assertThat(concentrations.findById(saved.getId()).orElseThrow().getCounterpartyId()).isNull();
    }

    @Test
    void aDependencyFigureOutsideZeroToOneIsRefused() {
        Company supplier = company("Supplier");
        Report report = report(supplier);

        assertThatThrownBy(() -> concentrations.saveAndFlush(figure(report, supplier, null, new BigDecimal("1.5000"))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void deletingAReportDeletesItsDependencyFigures() {
        Company supplier = company("Supplier");
        Report report = report(supplier);
        concentrations.save(figure(report, supplier, null, new BigDecimal("0.1000")));

        reports.deleteById(report.getId());

        assertThat(concentrations.findByReportIdOrderByIdAsc(report.getId())).isEmpty();
    }

    @Test
    void aReportRemembersWhenItsFiguresWereRead() {
        Report report = report(company("Supplier"));
        assertThat(reports.findById(report.getId()).orElseThrow().getFiguresReadAt()).isNull();

        Instant readAt = Instant.parse("2026-11-07T01:00:00Z");
        report.setFiguresReadAt(readAt);
        reports.save(report);

        assertThat(reports.findById(report.getId()).orElseThrow().getFiguresReadAt()).isEqualTo(readAt);
    }

    @Test
    void aNewRelationshipIsFoundByTheReportUnlessSaidOtherwise() {
        Company buyer = company("Buyer");
        Company supplier = company("Supplier");
        Report report = report(buyer);
        Relationship ordinary = relationships.save(new Relationship(report.getId(), buyer.getId(), supplier.getId(),
                RelationshipType.SUPPLIER, "wafers", EVIDENCE));
        Relationship byName = new Relationship(report.getId(), buyer.getId(), supplier.getId(),
                RelationshipType.CUSTOMER, "chips", EVIDENCE);
        byName.setFoundBy(FoundBy.NAME_SEARCH);
        byName = relationships.save(byName);

        assertThat(relationships.findById(ordinary.getId()).orElseThrow().getFoundBy()).isEqualTo(FoundBy.REPORT);
        assertThat(relationships.findById(byName.getId()).orElseThrow().getFoundBy()).isEqualTo(FoundBy.NAME_SEARCH);
    }

    @Test
    void aCompanyRemembersWhenItWasLastSearchedForByName() {
        Company holding = company("Holding");
        Instant searchedAt = Instant.parse("2026-11-07T02:00:00Z");
        nameSearches.save(new NameSearch(holding.getId(), searchedAt, 7));

        NameSearch found = nameSearches.findById(holding.getId()).orElseThrow();
        assertThat(found.getSearchedAt()).isEqualTo(searchedAt);
        assertThat(found.getFilingsFound()).isEqualTo(7);

        found.searchedAgain(searchedAt.plusSeconds(60), 0);
        nameSearches.save(found);
        assertThat(nameSearches.findById(holding.getId()).orElseThrow().getFilingsFound()).isZero();
        assertThat(nameSearches.count()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void aNameSearchCannotReportAFewerThanZeroFilings() {
        Company holding = company("Holding");

        assertThatThrownBy(() -> nameSearches.saveAndFlush(new NameSearch(holding.getId(), Instant.now(), -1)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void aRunningServerIsSavedAndRenewed() {
        UUID id = UUID.randomUUID();
        workers.save(new Worker(id, Instant.parse("2026-11-07T03:00:00Z")));

        Worker worker = workers.findById(id).orElseThrow();
        worker.setLastSeenAt(Instant.parse("2026-11-07T03:00:30Z"));
        workers.save(worker);

        assertThat(workers.findById(id).orElseThrow().getLastSeenAt()).isEqualTo(Instant.parse("2026-11-07T03:00:30Z"));
    }

    @Test
    void runningWorkRemembersTheServerThatOwnsIt() {
        UUID server = UUID.randomUUID();
        Portfolio portfolio = portfolio();
        assertThat(portfolios.findById(portfolio.getId()).orElseThrow().getNewsWorkerId()).isNull();
        portfolio.setNewsWorkerId(server);
        portfolios.save(portfolio);
        Holding holding = holdings.save(new Holding(portfolio.getId(), "AAPL", null));
        assertThat(holdings.findById(holding.getId()).orElseThrow().getWorkerId()).isNull();
        holding.setWorkerId(server);
        holdings.save(holding);
        Company company = company("Supplier");
        assertThat(companies.findById(company.getId()).orElseThrow().getReportWorkerId()).isNull();
        company.setReportWorkerId(server);
        companies.save(company);

        assertThat(portfolios.findById(portfolio.getId()).orElseThrow().getNewsWorkerId()).isEqualTo(server);
        assertThat(holdings.findById(holding.getId()).orElseThrow().getWorkerId()).isEqualTo(server);
        assertThat(companies.findById(company.getId()).orElseThrow().getReportWorkerId()).isEqualTo(server);
    }

    @Test
    void theAiCallRecordAcceptsTheNameSearch() {
        AiCall saved = aiCalls.save(new AiCall(AiPurpose.NAME_SEARCH, "test-model", "prompt", null, "answer",
                AiOutcome.ANSWERED, null, Instant.parse("2035-05-05T01:00:00Z"), 5));

        assertThat(aiCalls.findById(saved.getId()).orElseThrow().getPurpose()).isEqualTo(AiPurpose.NAME_SEARCH);
    }
}
