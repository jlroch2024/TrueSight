package com.truesight.briefing;

import com.lowagie.text.Font;
import com.lowagie.text.pdf.BaseFont;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * IBM Plex Sans, the app's own typeface, embedded in the briefing so it looks like the app. The two TTF files (Regular
 * and SemiBold, from IBM's Plex release, under the SIL Open Font License in {@code OFL.txt}) are read once.
 * OpenPDF cannot embed the website's web fonts, which is why these are separate files.
 */
@Component
class BriefingFonts {

    static final Color INK = new Color(0x1B, 0x23, 0x33);
    static final Color MUTED = new Color(0x5B, 0x67, 0x7D);
    static final Color TEAL = new Color(0x0F, 0x8F, 0x80);

    private final BaseFont regular = load("IBMPlexSans-Regular.ttf");
    private final BaseFont semiBold = load("IBMPlexSans-SemiBold.ttf");

    Font title() {
        return new Font(semiBold, 34, Font.NORMAL, INK);
    }

    Font heading() {
        return new Font(semiBold, 18, Font.NORMAL, TEAL);
    }

    Font subheading() {
        return new Font(semiBold, 12, Font.NORMAL, INK);
    }

    Font body() {
        return new Font(regular, 10, Font.NORMAL, INK);
    }

    Font strong() {
        return new Font(semiBold, 10, Font.NORMAL, INK);
    }

    Font small() {
        return new Font(regular, 8.5f, Font.NORMAL, MUTED);
    }

    Font tag(Color colour) {
        return new Font(semiBold, 9, Font.NORMAL, colour);
    }

    Font lead() {
        return new Font(regular, 14, Font.NORMAL, MUTED);
    }

    private static BaseFont load(String file) {
        try {
            byte[] bytes = new ClassPathResource("fonts/" + file).getContentAsByteArray();
            // Identity-H and embedded: the glyphs travel in the file, whatever the reader has installed.
            return BaseFont.createFont(file, BaseFont.IDENTITY_H, BaseFont.EMBEDDED, BaseFont.CACHED, bytes, null);
        } catch (IOException e) {
            throw new UncheckedIOException("The briefing's typeface is missing.", e);
        } catch (com.lowagie.text.DocumentException e) {
            throw new IllegalStateException("The briefing's typeface could not be read.", e);
        }
    }
}
