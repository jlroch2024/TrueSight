package com.truesight.briefing;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.truesight.news.AlertEventResponse;
import com.truesight.news.Severity;
import com.truesight.portfolio.HoldingResponse;
import com.truesight.relationship.RelationshipResponse;
import com.truesight.relationship.RelationshipType;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

/**
 * Writes a briefing as a PDF with OpenPDF, in six sections: the cover, the supply chain graph, the holdings, the open
 * alerts, the sole- and single-source dependencies, and a note on the sources. It says only what {@link BriefingData}
 * holds, in the words {@link BriefingText} takes from the screen.
 */
@Component
class BriefingPdf {

    static final String NO_ALERTS = "No open alerts.";
    static final String NO_GRAPH = "No Supply Chain Yet";
    static final String NO_DEPENDENCIES = "No sole- or single-source dependencies.";

    private static final Color HIGH = new Color(0xD1, 0x43, 0x43);
    private static final Color MEDIUM = new Color(0xD9, 0x70, 0x1F);
    private static final Color LOW = new Color(0x5B, 0x67, 0x7D);
    private static final Color RULE = new Color(0xD5, 0xDB, 0xE5);

    private final BriefingFonts fonts;

    BriefingPdf(BriefingFonts fonts) {
        this.fonts = fonts;
    }

    byte[] write(BriefingData data) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 54, 54, 54, 54);
        try {
            PdfWriter.getInstance(document, out);
            document.addTitle(data.portfolioName() + " Briefing");
            document.addCreator("TrueSight");
            document.open();
            cover(document, data);
            graph(document, data);
            holdings(document, data);
            alerts(document, data);
            dependencies(document, data);
            sources(document);
            document.close();
        } catch (DocumentException e) {
            throw new IllegalStateException("The briefing could not be written.", e);
        }
        return out.toByteArray();
    }

    // 1. The cover.
    private void cover(Document document, BriefingData data) throws DocumentException {
        Paragraph spacer = new Paragraph(" ", fonts.body());
        spacer.setSpacingBefore(200);
        document.add(spacer);
        document.add(new Paragraph(data.portfolioName(), fonts.title()));
        Paragraph date = new Paragraph(BriefingText.date(data.date()), fonts.lead());
        date.setSpacingBefore(8);
        document.add(date);
        Paragraph prepared = new Paragraph("Prepared With TrueSight", fonts.subheading());
        prepared.setSpacingBefore(28);
        document.add(prepared);
    }

    // 2. The supply chain graph, as the picture the website sent. A picture that cannot be read leaves the empty
    // wording, as the workspace shows for a portfolio with no graph.
    private void graph(Document document, BriefingData data) throws DocumentException {
        document.newPage();
        heading(document, "Supply Chain");
        Image picture = picture(data.graphPng());
        if (picture == null) {
            document.add(new Paragraph(NO_GRAPH, fonts.body()));
        } else {
            picture.scaleToFit(document.right() - document.left(), document.top() - document.bottom() - 60);
            picture.setBorder(Rectangle.BOX);
            picture.setBorderColor(RULE);
            picture.setBorderWidth(0.5f);
            document.add(picture);
        }
        document.newPage();
    }

    private static Image picture(byte[] png) {
        if (png == null) {
            return null;
        }
        try {
            return Image.getInstance(png);
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    // 3. The holdings: ticker, name, weight, report and filing date.
    private void holdings(Document document, BriefingData data) throws DocumentException {
        heading(document, "Holdings");
        if (data.holdings().isEmpty()) {
            document.add(new Paragraph("No holdings yet.", fonts.body()));
            return;
        }
        PdfPTable table = new PdfPTable(new float[] {1.1f, 3.4f, 2f, 1.2f, 1.8f});
        table.setWidthPercentage(100);
        for (String column : List.of("Ticker", "Company", "Weight", "Report", "Filed")) {
            table.addCell(cell(column, fonts.strong(), true));
        }
        for (HoldingResponse holding : data.holdings()) {
            table.addCell(cell(holding.ticker(), fonts.strong(), false));
            table.addCell(cell(holding.companyDisplayName() != null
                    ? holding.companyDisplayName()
                    : BriefingText.status(holding.status()), fonts.body(), false));
            table.addCell(cell(BriefingText.weight(holding.weight()), fonts.body(), false));
            table.addCell(cell(holding.reportType() == null ? "-" : holding.reportType(), fonts.body(), false));
            table.addCell(cell(BriefingText.date(holding.reportDate()), fonts.body(), false));
        }
        table.setHeaderRows(1);
        document.add(table);
    }

    // 4. The open alerts, most severe first as the alert list already is.
    private void alerts(Document document, BriefingData data) throws DocumentException {
        document.newPage();
        heading(document, "Open Alerts");
        List<AlertEventResponse> events = data.alerts().alerts();
        if (events.isEmpty()) {
            document.add(new Paragraph(NO_ALERTS, fonts.body()));
            return;
        }
        for (AlertEventResponse event : events) {
            alert(document, event, data);
        }
    }

    private void alert(Document document, AlertEventResponse event, BriefingData data) throws DocumentException {
        AlertEventResponse.Headline headline = event.headline();
        AlertEventResponse.Assessment assessment = headline.assessment();
        boolean weighted = data.alerts().weighted();

        Paragraph tags = new Paragraph();
        tags.setSpacingBefore(14);
        tags.add(new Phrase(BriefingText.severity(assessment.severity()).toUpperCase() + "   ",
                fonts.tag(colourOf(assessment.severity()))));
        tags.add(new Phrase(BriefingText.eventType(assessment.eventType()) + "   "
                + BriefingText.confidence(assessment.confidence()) + " Confidence", fonts.small()));
        document.add(tags);

        document.add(new Paragraph(headline.article().title(), fonts.subheading()));
        String source = headline.article().source() == null ? "Source Not Recorded" : headline.article().source();
        document.add(new Paragraph(source + "  -  "
                + BriefingText.date(headline.article().publishedAt().atZone(java.time.ZoneOffset.UTC).toLocalDate()),
                fonts.small()));
        if (assessment.summary() != null && !assessment.summary().isBlank()) {
            Paragraph summary = new Paragraph(assessment.summary(), fonts.body());
            summary.setSpacingBefore(3);
            document.add(summary);
        }

        Paragraph exposure = new Paragraph();
        exposure.setSpacingBefore(4);
        exposure.add(new Phrase("Exposure  ", fonts.small()));
        exposure.add(new Phrase(BriefingText.exposureFigure(event.exposure(), weighted) + " "
                + BriefingText.exposureLabel(weighted, data.alerts().holdingCount()), fonts.strong()));
        document.add(exposure);

        Paragraph reach = new Paragraph();
        reach.add(new Phrase("Reach  ", fonts.small()));
        reach.add(new Phrase(BriefingText.reachedCount(event.holdings().size()) + " ("
                + BriefingText.reachSplit(event.holdings()) + ")", fonts.strong()));
        document.add(reach);
        for (AlertEventResponse.ReachedHolding reached : event.holdings()) {
            Paragraph line = new Paragraph(reached.ticker() + "  " + BriefingText.path(reached), fonts.body());
            line.setIndentationLeft(12);
            document.add(line);
        }

        // Only a next step an analyst approved: none is printed otherwise, as the screen shows none.
        if (event.recommendation() != null && !event.recommendation().isBlank()) {
            Paragraph step = new Paragraph();
            step.setSpacingBefore(4);
            step.add(new Phrase("Suggested Next Step  ", fonts.small()));
            step.add(new Phrase(event.recommendation(), fonts.body()));
            document.add(step);
            document.add(new Paragraph("Reviewed by an Analyst", fonts.small()));
        }
    }

    // 5. The sole- and single-source dependencies, each with the report's own sentence.
    private void dependencies(Document document, BriefingData data) throws DocumentException {
        document.newPage();
        heading(document, "Sole- and Single-Source Dependencies");
        if (data.dependencies().isEmpty()) {
            document.add(new Paragraph(NO_DEPENDENCIES, fonts.body()));
            return;
        }
        for (RelationshipResponse link : data.dependencies()) {
            // SUPPLIER: the counterparty supplies the company. CUSTOMER: the counterparty buys from it.
            boolean counterpartySupplies = link.type() == RelationshipType.SUPPLIER;
            String supplier = (counterpartySupplies ? link.counterparty() : link.company()).displayName();
            String customer = (counterpartySupplies ? link.company() : link.counterparty()).displayName();

            Paragraph title = new Paragraph();
            title.setSpacingBefore(12);
            title.add(new Phrase(BriefingText.criticality(link.criticality()).toUpperCase() + "   ",
                    fonts.tag(BriefingFonts.TEAL)));
            title.add(new Phrase(supplier + " supplies " + customer, fonts.subheading()));
            document.add(title);

            if (link.criticalityEvidence() != null) {
                Paragraph evidence = new Paragraph("\"" + link.criticalityEvidence() + "\"", fonts.body());
                evidence.setSpacingBefore(2);
                evidence.setIndentationLeft(12);
                document.add(evidence);
            }
            RelationshipResponse.ReportSummary report = link.report();
            if (report != null) {
                document.add(new Paragraph("From the " + report.form() + " filed " + BriefingText.date(report.filingDate()),
                        fonts.small()));
            }
        }
    }

    // 6. Where the facts came from.
    private void sources(Document document) throws DocumentException {
        document.newPage();
        heading(document, "About These Sources");
        for (String line : List.of(
                "Suppliers and customers come from each company's latest annual report (a 10-K or 20-F) on the SEC website.",
                "News comes from Google News.",
                "Judgements about the news and the relationships between companies are made by Gemini, and reviewed by "
                        + "TrueSight's analysts where marked.",
                "This briefing is research support, not investment advice.")) {
            Paragraph paragraph = new Paragraph(line, fonts.body());
            paragraph.setSpacingAfter(6);
            document.add(paragraph);
        }
        Paragraph typeface = new Paragraph("Set in IBM Plex Sans, under the SIL Open Font License.", fonts.small());
        typeface.setSpacingBefore(14);
        document.add(typeface);
    }

    private void heading(Document document, String text) throws DocumentException {
        Paragraph heading = new Paragraph(text, fonts.heading());
        heading.setSpacingAfter(10);
        document.add(heading);
    }

    private PdfPCell cell(String text, Font font, boolean header) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setBorder(Rectangle.BOTTOM);
        cell.setBorderColor(RULE);
        cell.setBorderWidth(header ? 1f : 0.5f);
        cell.setPaddingTop(5);
        cell.setPaddingBottom(5);
        cell.setPaddingLeft(0);
        cell.setHorizontalAlignment(Element.ALIGN_LEFT);
        return cell;
    }

    private static Color colourOf(Severity severity) {
        return switch (severity) {
            case HIGH -> HIGH;
            case MEDIUM -> MEDIUM;
            case LOW -> LOW;
        };
    }
}
