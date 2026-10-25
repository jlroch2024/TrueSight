package com.truesight.news;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/** Reads Google News RSS without allowing the XML to load declarations or outside resources. */
public class GoogleNewsParser {

    private static final Logger log = LoggerFactory.getLogger(GoogleNewsParser.class);

    public List<NewsItem> parse(String xml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);

            NodeList items = factory.newDocumentBuilder()
                    .parse(new InputSource(new StringReader(xml)))
                    .getElementsByTagName("item");
            List<NewsItem> result = new ArrayList<>();
            int unreadableDates = 0;
            for (int index = 0; index < items.getLength(); index++) {
                Element item = (Element) items.item(index);
                String title = text(item, "title");
                String link = text(item, "link");
                String source = text(item, "source");
                String date = text(item, "pubDate");
                if (title.isBlank() || link.isBlank()) continue;
                if (date.isBlank()) {
                    unreadableDates++;
                    continue;
                }
                try {
                    Instant publishedAt = DateTimeFormatter.RFC_1123_DATE_TIME.parse(date, Instant::from);
                    String suffix = source.isBlank() ? "" : " - " + source;
                    if (!suffix.isEmpty() && title.endsWith(suffix)) {
                        title = title.substring(0, title.length() - suffix.length()).trim();
                    }
                    if (!title.isBlank()) result.add(new NewsItem(title, link, source, publishedAt));
                } catch (DateTimeParseException ignored) {
                    // An item without a readable publication date cannot be checked against the seven-day window.
                    unreadableDates++;
                }
            }
            if (unreadableDates > 0) {
                log.warn("Skipped {} Google News item(s) with an unreadable publication date.", unreadableDates);
            }
            return List.copyOf(result);
        } catch (Exception exception) {
            throw new IllegalArgumentException("Google News returned unreadable RSS.", exception);
        }
    }

    private String text(Element parent, String tagName) {
        NodeList nodes = parent.getElementsByTagName(tagName);
        if (nodes.getLength() == 0) return "";
        Node node = nodes.item(0);
        return node.getTextContent() == null ? "" : node.getTextContent().trim();
    }
}
