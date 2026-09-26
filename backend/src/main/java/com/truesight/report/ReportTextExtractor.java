package com.truesight.report;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;

import java.util.ArrayList;
import java.util.List;

/** Extracts readable report text while retaining paragraph and heading boundaries. */
public final class ReportTextExtractor {

    private ReportTextExtractor() { }

    public static String extract(String html) {
        Element body = Jsoup.parse(html).body();
        body.select("script, style, noscript, ix\\:header").remove();
        StringBuilder raw = new StringBuilder();
        append(body, raw);
        List<String> paragraphs = new ArrayList<>();
        for (String block : raw.toString().replace('\r', '\n').split("\\n{2,}")) {
            List<String> wrappedLines = new ArrayList<>();
            for (String line : block.split("\\n+")) {
                String cleaned = line.replaceAll("[\\t\\f ]+", " ").trim();
                if (!cleaned.isEmpty()) wrappedLines.add(cleaned);
            }
            if (wrappedLines.isEmpty()) continue;
            String paragraph = wrappedLines.getFirst();
            for (int i = 1; i < wrappedLines.size(); i++) {
                if (endsSentence(paragraph)) {
                    paragraphs.add(paragraph);
                    paragraph = wrappedLines.get(i);
                } else {
                    paragraph += " " + wrappedLines.get(i);
                }
            }
            paragraphs.add(paragraph);
        }
        return String.join("\n", paragraphs);
    }

    private static boolean endsSentence(String line) {
        return line.matches("(?s).*[.!?][\\\"'’”)]*$") || line.endsWith(":");
    }

    private static void append(Node node, StringBuilder output) {
        if (node instanceof TextNode text) {
            output.append(text.getWholeText());
            return;
        }
        if (!(node instanceof Element element)) return;
        String tag = element.normalName();
        if (tag.equals("br")) {
            output.append('\n');
            return;
        }
        boolean paragraphBoundary = switch (tag) {
            case "address", "article", "blockquote", "dd", "dl", "dt", "figcaption", "h1", "h2", "h3",
                    "h4", "h5", "h6", "li", "p", "pre", "section" -> true;
            default -> false;
        };
        boolean block = switch (tag) {
            case "address", "article", "blockquote", "dd", "div", "dl", "dt", "fieldset", "figcaption",
                    "figure", "footer", "form", "h1", "h2", "h3", "h4", "h5", "h6", "header", "hr",
                    "li", "main", "ol", "p", "pre", "section", "table", "tbody", "td", "tfoot", "th",
                    "thead", "tr", "ul" -> true;
            default -> false;
        };
        if (block) output.append(paragraphBoundary ? "\n\n" : "\n");
        for (Node child : node.childNodes()) append(child, output);
        if (block) output.append(paragraphBoundary ? "\n\n" : "\n");
    }
}
