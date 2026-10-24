package com.truesight.company;

import com.truesight.report.Report;
import com.truesight.report.ReportRepository;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * A company's name as every page shows it: spelled the way the company's own annual report spells it. The stored
 * name never changes; this is worked out from it, here and nowhere else, and sent beside it in every response that
 * carries a company. The website never reformats a name itself.
 *
 * <p>The rule, word by word, applied to the stored name (the Spell Each Company as Its Own Report Does story):
 * <ol>
 *   <li>The SEC's registration mark after a slash goes: "QUALCOMM INC/DE" becomes "QUALCOMM INC".</li>
 *   <li>Legal endings are dropped from the end, one word at a time, then any trailing "&amp;" and commas: "ELI LILLY
 *       &amp; Co" becomes "ELI LILLY". A name is never emptied: "Company" stays.</li>
 *   <li>A trailing "COM" joins the word before it as ".com": "AMAZON COM" becomes "AMAZON.com".</li>
 *   <li>A word that already has a lower-case letter is never changed ("SK Hynix", "Apple").</li>
 *   <li>A word in capitals, when the company has a report, becomes its most frequent spelling in that report:
 *       whole-word, ignoring case, but not counting matches written all in lower case (web addresses such as
 *       nvidia.com). When two spellings are as frequent, the one the report writes first wins.</li>
 *   <li>Any word still in capitals, when the whole stored name is in capitals (the SEC's style): words of five
 *       letters or more become Title Case ("MICROSOFT" becomes "Microsoft"); shorter ones stay, as they are usually
 *       initials ("ASML"). When the stored name has lower-case letters elsewhere, its capitals were chosen, so they
 *       stay ("NVIDIA Corporation" reads "NVIDIA").</li>
 * </ol>
 *
 * <p>Reading a report is the slow part, so it happens only when step 5 has a word in capitals to spell, and only once
 * per company and report while the backend runs: each result is kept, keyed by the company and its newest report, so
 * a newer report spells the name afresh.
 */
@Component
public class CompanyDisplayNames {

    /** The same endings {@link CompanyDirectory} ignores, plus Singapore's "Pte" and the US "PBC". */
    private static final Set<String> LEGAL_ENDINGS = Set.of(
            "inc", "incorporated", "corp", "corporation", "co", "company", "ltd", "limited",
            "plc", "nv", "sa", "ag", "se", "llc", "lp", "pte", "pbc");

    /** What the SEC writes after a slash at the end of a name, as in {@link CompanyDirectory}. */
    private static final Pattern SEC_REGISTRATION_MARK = Pattern.compile("\\s*/\\s*[A-Za-z]+\\s*/?\\s*$");

    private static final Pattern LETTERS = Pattern.compile("\\p{L}+");

    /** Step 6 leaves words shorter than this in capitals: they are usually initials. */
    private static final int TITLE_CASE_FROM = 5;

    private final ReportRepository reports;
    private final Map<Key, String> spelled = new ConcurrentHashMap<>();

    public CompanyDisplayNames(ReportRepository reports) {
        this.reports = reports;
    }

    /** The company's name as the website shows it, e.g. "NVIDIA" for "NVIDIA CORP". */
    public String of(Company company) {
        String stored = company.getName();
        List<Word> words = words(stored);
        if (words.stream().noneMatch(Word::inCapitals)) return join(words);

        Optional<Long> reportId = reports.findLatestIdByCompanyId(company.getId());
        if (reportId.isEmpty()) return spell(words, stored, null);
        return spelled.computeIfAbsent(new Key(company.getId(), reportId.get()),
                key -> spell(words, stored, reports.findById(key.reportId()).map(Report::getText).orElse(null)));
    }

    /** Steps 1 to 3: the stored name's words, without its registration mark and legal endings. */
    private static List<Word> words(String stored) {
        String name = SEC_REGISTRATION_MARK.matcher(stored).replaceAll("").trim();
        List<String> parts = new ArrayList<>(Arrays.asList(name.split("\\s+")));
        boolean dropped = true;
        while (dropped) {
            dropped = false;
            while (parts.size() > 1 && LEGAL_ENDINGS.contains(ending(parts.getLast()))) {
                parts.removeLast();
                dropped = true;
            }
            while (parts.size() > 1 && parts.getLast().equals("&")) {
                parts.removeLast();
                dropped = true;
            }
            String last = parts.getLast().replaceAll(",+$", "");
            if (!last.isEmpty() && !last.equals(parts.getLast())) {
                parts.set(parts.size() - 1, last);
                dropped = true;
            }
        }

        List<Word> words = new ArrayList<>(parts.stream().map(part -> new Word(part, "")).toList());
        if (words.size() > 1 && words.getLast().text().equals("COM")) {
            words.removeLast();
            words.set(words.size() - 1, new Word(words.getLast().text(), ".com"));
        }
        return words;
    }

    /** A word as a legal ending is written once punctuation and capitals are gone: "N.V." becomes "nv". */
    private static String ending(String word) {
        return word.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    /** Steps 4 to 6. {@code report} is the report's text, or null when the company has none. */
    private static String spell(List<Word> words, String stored, String report) {
        boolean storedInCapitals = stored.chars().noneMatch(Character::isLowerCase);
        return words.stream().map(word -> {
            if (!word.inCapitals()) return word.text() + word.suffix();
            String spelling = report == null ? null : mostFrequentSpelling(word.text(), report);
            if (spelling == null && storedInCapitals && letterCount(word.text()) >= TITLE_CASE_FROM) {
                spelling = titleCase(word.text());
            }
            return (spelling == null ? word.text() : spelling) + word.suffix();
        }).collect(Collectors.joining(" "));
    }

    /**
     * How the report most often writes {@code word}, as a whole word in any case, leaving out matches written all in
     * lower case. Null when the report never writes it.
     */
    static String mostFrequentSpelling(String word, String report) {
        Pattern wholeWord = Pattern.compile("(?<![\\p{L}\\p{N}])" + Pattern.quote(word) + "(?![\\p{L}\\p{N}])",
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        Map<String, Integer> counts = new LinkedHashMap<>();
        Matcher matcher = wholeWord.matcher(report);
        while (matcher.find()) {
            String found = matcher.group();
            if (!found.equals(found.toLowerCase(Locale.ROOT))) counts.merge(found, 1, Integer::sum);
        }
        String best = null;
        for (Map.Entry<String, Integer> count : counts.entrySet()) {
            if (best == null || count.getValue() > counts.get(best)) best = count.getKey();
        }
        return best;
    }

    private static int letterCount(String word) {
        return (int) word.chars().filter(Character::isLetter).count();
    }

    /** "MANUFACTURING" becomes "Manufacturing"; each run of letters in "SK-HYNIX" is done on its own. */
    private static String titleCase(String word) {
        return LETTERS.matcher(word).replaceAll(letters -> {
            String run = letters.group();
            return run.charAt(0) + run.substring(1).toLowerCase(Locale.ROOT);
        });
    }

    private static String join(List<Word> words) {
        return words.stream().map(word -> word.text() + word.suffix()).collect(Collectors.joining(" "));
    }

    /** One word of the name. {@code suffix} is ".com" for the word a trailing "COM" joined, otherwise empty. */
    private record Word(String text, String suffix) {

        /** Has capitals and no lower-case letter: the SEC's style, or initials. */
        boolean inCapitals() {
            return text.chars().anyMatch(Character::isUpperCase) && text.chars().noneMatch(Character::isLowerCase);
        }
    }

    private record Key(Long companyId, Long reportId) {
    }
}
