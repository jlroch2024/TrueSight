package com.truesight.news;

import com.truesight.report.ReportTextExtractor;
import com.truesight.report.SecClient.SecCurrentReport;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Which of a company's 8-Ks a news check reads, and the article each one becomes. An 8-K is read only when it reports
 * one of {@link #ITEMS}; any other (results, executive changes, shareholder votes) is never downloaded, saved or sent to
 * the AI. Its headline is the company's name, the first kept item, and the opening of that item's own text, so Filter
 * Out Noise judges the company's own words: "Clorox, Item 1.05: On [date], the Company detected…". When the item's
 * text says nothing itself (it only points to an attachment), or the 8-K cannot be downloaded, the headline is the
 * item's name: "Clorox Files an 8-K: Other Events".
 */
public final class CurrentReports {

    /** The source shown for an 8-K, where a news article shows its publisher. */
    public static final String SOURCE = "SEC 8-K";

    /** The nine items read, with their official names. */
    static final Map<String, String> ITEMS = Map.of(
            "1.02", "Termination of a Material Definitive Agreement",
            "1.03", "Bankruptcy or Receivership",
            "1.05", "Material Cybersecurity Incidents",
            "2.04", "Triggering Events That Accelerate or Increase a Direct Financial Obligation",
            "2.05", "Costs Associated with Exit or Disposal Activities",
            "2.06", "Material Impairments",
            "3.01", "Notice of Delisting or Failure to Satisfy a Continued Listing Rule or Standard",
            "4.02", "Non-Reliance on Previously Issued Financial Statements",
            "8.01", "Other Events");

    /** The longest opening of an item's text in a headline, "…" included. */
    static final int OPENING_LIMIT = 300;
    // An item's text shorter than this says nothing itself.
    private static final int SHORTEST_OPENING = 40;
    // Any item's heading, which ends the item before it.
    private static final Pattern ANY_HEADING = Pattern.compile("(?i)^item\\s+\\d+\\.\\d{2}\\b.*");
    private static final Pattern EXHIBIT = Pattern.compile("(?i)\\bexhibit\\s+\\d");
    private static final Pattern SENTENCE_END = Pattern.compile("(?<=[.!?][\"'’”)]?)\\s");

    private CurrentReports() {
    }

    /** The filings that report at least one of the nine items, in the order given. */
    public static List<SecCurrentReport> kept(List<SecCurrentReport> filings) {
        return filings.stream().filter(filing -> firstKeptItem(filing) != null).toList();
    }

    /**
     * The article an 8-K becomes. document is the downloaded filing, or null when it could not be downloaded.
     */
    public static NewsItem newsItem(String companyName, SecCurrentReport filing, String document) {
        String item = firstKeptItem(filing);
        if (item == null) throw new IllegalArgumentException("The 8-K reports none of the items read.");
        String opening = document == null ? null : opening(ReportTextExtractor.extract(document), item);
        String title = opening == null
                ? "%s Files an 8-K: %s".formatted(companyName, ITEMS.get(item))
                : "%s, Item %s: %s".formatted(companyName, item, opening);
        return new NewsItem(title, filing.url(), SOURCE, filing.acceptedAt());
    }

    private static String firstKeptItem(SecCurrentReport filing) {
        return filing.items().stream().filter(ITEMS::containsKey).findFirst().orElse(null);
    }

    /**
     * The opening of an item's own text, after its heading ("Item 8.01 Other Events."), up to {@link #OPENING_LIMIT}
     * characters, cut at a word with "…". Null when the item's heading is not found, or its text says nothing itself:
     * under 40 characters, or a first sentence that names an exhibit ("…attached as Exhibit 99.1").
     */
    static String opening(String text, String item) {
        Pattern heading = Pattern.compile("(?i)^item\\s+" + Pattern.quote(item) + "(?![\\d])[\\s.:–—-]*");
        Pattern name = Pattern.compile("(?i)^" + Pattern.quote(ITEMS.get(item)) + "[\\s.:–—-]*");
        StringBuilder found = null;
        for (String line : text.replace(' ', ' ').split("\n")) {
            String trimmed = line.replaceAll("\\s+", " ").trim();
            if (found == null) {
                var start = heading.matcher(trimmed);
                if (!start.lookingAt()) continue;
                // The heading itself, and the item's name after it, are not the item's text.
                found = new StringBuilder(name.matcher(trimmed.substring(start.end())).replaceFirst(""));
            } else if (ANY_HEADING.matcher(trimmed).matches()) {
                break;
            } else {
                found.append(' ').append(trimmed);
            }
            if (found.length() > OPENING_LIMIT) break;
        }
        if (found == null) return null;
        String opening = found.toString().trim();
        if (opening.length() < SHORTEST_OPENING) return null;
        if (EXHIBIT.matcher(SENTENCE_END.split(opening, 2)[0]).find()) return null;
        if (opening.length() <= OPENING_LIMIT) return opening;
        String cut = opening.substring(0, OPENING_LIMIT - 1); // room for the "…"
        int lastSpace = cut.lastIndexOf(' ');
        if (lastSpace > 0) cut = cut.substring(0, lastSpace);
        return cut.replaceAll("[\\s,;:]+$", "") + "…";
    }
}
