package com.truesight.history;

import com.truesight.relationship.RelationshipType;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * Turns the reports read into the year-by-year picture. Pure: it works from what it is given, so it is tested without a
 * database.
 *
 * <p>For each year from the earliest report's to the latest's, each company counts as having the relationships of the
 * newest report it had filed by the end of that year; a year in which it had filed none shows nothing of it. A
 * relationship is matched from year to year by its key (company, counterparty, type): New if the year before did not
 * have it (every relationship of the first year is New, as nothing came before), Kept if it did. What the year before
 * had and this one does not is listed as gone.
 */
final class HistoryTimeline {

    private HistoryTimeline() {
    }

    /** One report: whose it is, when and where it was filed, and the relationships it names. */
    record Source(Long companyId, LocalDate filingDate, String reportUrl, List<Edge> edges) {
    }

    /** A relationship a report names, with the company the report belongs to left to its {@link Source}. */
    record Edge(Long counterpartyId, RelationshipType type, String provides, String evidence) {
    }

    static List<HistoryResponse.Year> years(List<Source> sources) {
        if (sources.isEmpty()) return List.of();
        int first = sources.stream().mapToInt(source -> source.filingDate().getYear()).min().orElseThrow();
        int last = sources.stream().mapToInt(source -> source.filingDate().getYear()).max().orElseThrow();
        Map<Long, List<Source>> byCompany = new HashMap<>();
        sources.forEach(source -> byCompany.computeIfAbsent(source.companyId(), id -> new ArrayList<>()).add(source));

        List<HistoryResponse.Year> years = new ArrayList<>();
        Set<String> before = null;
        for (int year = first; year <= last; year++) {
            LocalDate endOfYear = LocalDate.of(year, 12, 31);
            Map<String, HistoryResponse.Entry> entries = new HashMap<>();
            for (Map.Entry<Long, List<Source>> company : byCompany.entrySet()) {
                Optional<Source> newest = company.getValue().stream()
                        .filter(source -> !source.filingDate().isAfter(endOfYear))
                        .max(Comparator.comparing(Source::filingDate));
                if (newest.isEmpty()) continue;
                for (Edge edge : newest.get().edges()) {
                    String key = company.getKey() + "-" + edge.counterpartyId() + "-" + edge.type();
                    Set<String> named = before;
                    entries.putIfAbsent(key, new HistoryResponse.Entry(key, company.getKey(), edge.counterpartyId(),
                            edge.type(), edge.provides(), edge.evidence(), newest.get().reportUrl(),
                            newest.get().filingDate(),
                            named != null && named.contains(key)
                                    ? HistoryResponse.Change.KEPT : HistoryResponse.Change.NEW));
                }
            }
            List<HistoryResponse.Entry> ordered = entries.values().stream()
                    .sorted(Comparator.comparing(HistoryResponse.Entry::companyId)
                            .thenComparing(HistoryResponse.Entry::counterpartyId)
                            .thenComparing(entry -> entry.type().name()))
                    .toList();
            Set<String> gone = new TreeSet<>();
            if (before != null) {
                gone.addAll(before);
                gone.removeAll(entries.keySet());
            }
            years.add(new HistoryResponse.Year(year, ordered, List.copyOf(gone)));
            before = new HashSet<>(entries.keySet());
        }
        return years;
    }
}
