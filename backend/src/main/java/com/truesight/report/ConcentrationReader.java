package com.truesight.report;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Turns a filing's tagged figures into dependency figures: every {@code us-gaap:ConcentrationRiskPercentage1} fact
 * whose context names a customer or supplier (on {@code ConcentrationRiskByTypeAxis}) and a party (on any other axis
 * but the benchmark). Pure: it reads the two files' text and touches no database or network.
 *
 * <p>The facts are read with the JDK's namespace-aware XML parser, with doctypes and outside resources refused.
 */
public final class ConcentrationReader {

    private static final String XBRLI = "http://www.xbrl.org/2003/instance";
    private static final String XBRLDI = "http://xbrl.org/2006/xbrldi";
    private static final String LINK = "http://www.xbrl.org/2003/linkbase";
    private static final String XLINK = "http://www.w3.org/1999/xlink";
    private static final String TERSE_ROLE = "http://www.xbrl.org/2003/role/terseLabel";
    private static final String STANDARD_ROLE = "http://www.xbrl.org/2003/role/label";

    private static final String TYPE_AXIS = "ConcentrationRiskByTypeAxis";
    private static final String BENCHMARK_AXIS = "ConcentrationRiskByBenchmarkAxis";
    /** The axis a filing usually names its customer or supplier on; it wins over any other axis in the context. */
    private static final String PARTY_AXIS = "MajorCustomersAxis";

    /** The longest label stored: the width of the {@code concentrations.label} column. */
    static final int MAX_LABEL = 300;

    /** A label with any of these words names no company. */
    private static final Pattern UNNAMED = Pattern.compile(
            "\\b(customers?|distributors?|suppliers?|vendors?|largest|others?|top)\\b", Pattern.CASE_INSENSITIVE);

    private ConcentrationReader() {
    }

    /** One figure. {@code named} is false when the label names no company ("Customer A", "Ten Largest Customers"). */
    public record Figure(String label, boolean named, ConcentrationKind kind, ConcentrationMeasure measure,
                         BigDecimal share, LocalDate periodStart, LocalDate periodEnd) {
    }

    private record Context(LocalDate start, LocalDate end, Map<String, String> members) {
    }

    /**
     * @param instance the file ending {@code _htm.xml}
     * @param labels   the file ending {@code _lab.xml}
     * @throws IllegalArgumentException if either file cannot be read as XML
     */
    public static List<Figure> read(String instance, String labels) {
        Element root = parse(instance).getDocumentElement();
        Map<String, String> labelOf = labelsByMember(parse(labels).getDocumentElement());

        Map<String, Context> contexts = new HashMap<>();
        for (Element context : children(root, XBRLI, "context")) {
            Context read = readContext(context);
            if (read != null) contexts.put(context.getAttribute("id"), read);
        }

        List<Figure> figures = new ArrayList<>();
        // A filing sometimes tags the same figure twice; it is kept once.
        Set<String> seen = new HashSet<>();
        for (Element fact : children(root, null, "ConcentrationRiskPercentage1")) {
            if (fact.getNamespaceURI() == null || !fact.getNamespaceURI().startsWith("http://fasb.org/us-gaap/")) continue;
            Context context = contexts.get(fact.getAttribute("contextRef"));
            if (context == null || context.end() == null) continue;

            ConcentrationKind kind = switch (localName(context.members().get(TYPE_AXIS))) {
                case "CustomerConcentrationRiskMember" -> ConcentrationKind.CUSTOMER;
                case "SupplierConcentrationRiskMember" -> ConcentrationKind.SUPPLIER;
                default -> null;
            };
            if (kind == null) continue;

            // The party is on the customers axis if the context has one, else on the first other axis in the file's
            // order (the members are kept in document order), never chosen by chance.
            String party = context.members().get(PARTY_AXIS);
            if (party == null) {
                for (Map.Entry<String, String> member : context.members().entrySet()) {
                    if (!member.getKey().equals(TYPE_AXIS) && !member.getKey().equals(BENCHMARK_AXIS)) {
                        party = member.getValue();
                        break;
                    }
                }
            }
            if (party == null) continue;

            BigDecimal share = shareOf(fact.getTextContent());
            if (share == null) continue;

            String label = labelOf.getOrDefault(localName(party), splitAtCapitals(party));
            if (label.length() > MAX_LABEL) label = label.substring(0, MAX_LABEL).trim();
            ConcentrationMeasure measure = measureOf(context.members().get(BENCHMARK_AXIS));
            if (!seen.add(String.join("|", label, kind.name(), measure.name(), String.valueOf(context.start()),
                    String.valueOf(context.end())))) {
                continue;
            }
            figures.add(new Figure(label, !UNNAMED.matcher(label).find(), kind, measure, share, context.start(),
                    context.end()));
        }
        return figures;
    }

    private static Context readContext(Element context) {
        Map<String, String> members = new LinkedHashMap<>();
        for (Element member : descendants(context, XBRLDI, "explicitMember")) {
            members.putIfAbsent(localName(member.getAttribute("dimension")), member.getTextContent().trim());
        }
        Element period = first(children(context, XBRLI, "period"));
        if (period == null) return null;
        try {
            Element instant = first(children(period, XBRLI, "instant"));
            if (instant != null) {
                return new Context(null, LocalDate.parse(instant.getTextContent().trim()), members);
            }
            Element start = first(children(period, XBRLI, "startDate"));
            Element end = first(children(period, XBRLI, "endDate"));
            if (start == null || end == null) return null;
            return new Context(LocalDate.parse(start.getTextContent().trim()),
                    LocalDate.parse(end.getTextContent().trim()), members);
        } catch (DateTimeParseException unreadableDate) {
            return null;
        }
    }

    /** A fraction from 0 to 1, as the filing tags it (0.91 for 91%), or null if it is not one. */
    private static BigDecimal shareOf(String text) {
        try {
            BigDecimal value = new BigDecimal(text.trim());
            if (value.signum() < 0 || value.compareTo(BigDecimal.ONE) > 0) return null;
            return value.setScale(4, RoundingMode.HALF_UP);
        } catch (NumberFormatException unreadable) {
            return null;
        }
    }

    private static ConcentrationMeasure measureOf(String benchmarkMember) {
        String name = localName(benchmarkMember);
        if (name.contains("Revenue") || name.contains("Sales")) return ConcentrationMeasure.REVENUE;
        if (name.contains("Receivable")) return ConcentrationMeasure.RECEIVABLES;
        if (name.contains("Purchase") || name.contains("CostOfGoods")) return ConcentrationMeasure.PURCHASES;
        return ConcentrationMeasure.OTHER;
    }

    /**
     * Each member's label, by the member's name without its prefix ("AppleIncMember"). A member is tied to its labels
     * by {@code link:loc} (its address ends "#prefix_Name") and {@code link:labelArc} (loc to label). The terse label
     * wins over the standard one, and " [Member]" is dropped.
     */
    private static Map<String, String> labelsByMember(Element root) {
        Map<String, String> memberOfLoc = new HashMap<>();
        for (Element loc : descendants(root, LINK, "loc")) {
            String href = loc.getAttributeNS(XLINK, "href");
            String concept = href.substring(href.indexOf('#') + 1);
            memberOfLoc.put(loc.getAttributeNS(XLINK, "label"), concept.substring(concept.indexOf('_') + 1));
        }
        Map<String, String> terse = new HashMap<>();
        Map<String, String> standard = new HashMap<>();
        for (Element label : descendants(root, LINK, "label")) {
            String resource = label.getAttributeNS(XLINK, "label");
            String role = label.getAttributeNS(XLINK, "role");
            String text = label.getTextContent().replace(" [Member]", "").trim();
            if (TERSE_ROLE.equals(role)) terse.putIfAbsent(resource, text);
            if (STANDARD_ROLE.equals(role)) standard.putIfAbsent(resource, text);
        }
        Map<String, String> labelOf = new HashMap<>();
        for (Element arc : descendants(root, LINK, "labelArc")) {
            String member = memberOfLoc.get(arc.getAttributeNS(XLINK, "from"));
            String resource = arc.getAttributeNS(XLINK, "to");
            if (member == null) continue;
            String text = terse.getOrDefault(resource, standard.get(resource));
            if (text != null && !text.isBlank()) labelOf.merge(member, text, (old, now) -> old);
        }
        return labelOf;
    }

    /** "AppleIncMember" becomes "Apple Inc". */
    private static String splitAtCapitals(String member) {
        String name = localName(member);
        if (name.endsWith("Member")) name = name.substring(0, name.length() - "Member".length());
        return name.replaceAll("(?<=[a-z0-9])(?=[A-Z])|(?<=[A-Z])(?=[A-Z][a-z])", " ").trim();
    }

    /** The part after the prefix: "crus:AppleIncMember" becomes "AppleIncMember". Null or blank becomes "". */
    private static String localName(String qualified) {
        if (qualified == null) return "";
        return qualified.substring(qualified.indexOf(':') + 1).trim();
    }

    private static org.w3c.dom.Document parse(String xml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            return factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
        } catch (Exception unreadable) {
            throw new IllegalArgumentException("The tagged figures could not be read.", unreadable);
        }
    }

    private static List<Element> children(Element parent, String namespace, String name) {
        List<Element> found = new ArrayList<>();
        for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node instanceof Element element && name.equals(element.getLocalName())
                    && (namespace == null || namespace.equals(element.getNamespaceURI()))) {
                found.add(element);
            }
        }
        return found;
    }

    private static List<Element> descendants(Element parent, String namespace, String name) {
        NodeList nodes = parent.getElementsByTagNameNS(namespace, name);
        List<Element> found = new ArrayList<>();
        for (int i = 0; i < nodes.getLength(); i++) found.add((Element) nodes.item(i));
        return found;
    }

    private static Element first(List<Element> elements) {
        return elements.isEmpty() ? null : elements.get(0);
    }
}
