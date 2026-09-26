package com.truesight.portfolio;

import com.truesight.common.ApiException;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Turns an uploaded CSV into holdings: a ticker, and a weight when the file has one.
 *
 * <p>The ticker column is the first whose name, ignoring spaces and capitals, is "ticker" or "symbol". The weight
 * column is optional, and accepts both {@code 12.5} and {@code 12.5%}. Rows with an empty ticker are skipped. Any
 * problem is a 400 that says what is wrong, so nothing is saved from a bad file.
 */
@Component
public class HoldingCsvReader {

    /** One row of the file: an upper-case ticker, and a weight or null. */
    public record Row(String ticker, BigDecimal weight) {
    }

    public List<Row> read(InputStream in) {
        List<List<String>> lines;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            lines = reader.lines().filter(line -> !line.isBlank()).map(HoldingCsvReader::split).toList();
        } catch (IOException | java.io.UncheckedIOException e) {
            throw ApiException.badRequest("The file could not be read.");
        }
        if (lines.isEmpty()) {
            throw ApiException.badRequest("The file is empty.");
        }

        List<String> header = lines.get(0);
        int tickerColumn = findColumn(header, "ticker", "symbol");
        if (tickerColumn < 0) {
            throw ApiException.badRequest("The file needs a ticker column.");
        }
        int weightColumn = findColumn(header, "weight");

        List<Row> rows = new ArrayList<>();
        for (int i = 1; i < lines.size(); i++) {
            List<String> cells = lines.get(i);
            String ticker = cell(cells, tickerColumn).toUpperCase(Locale.ROOT);
            if (ticker.isEmpty()) {
                continue;
            }
            if (ticker.length() > 20) {
                throw ApiException.badRequest("Row " + (i + 1) + ": \"" + ticker + "\" is too long to be a ticker.");
            }
            BigDecimal weight = weightColumn < 0 ? null : weight(cell(cells, weightColumn), i + 1);
            rows.add(new Row(ticker, weight));
        }
        if (rows.isEmpty()) {
            throw ApiException.badRequest("The file has no holdings.");
        }
        return rows;
    }

    /** The first column whose name, without spaces and capitals, is one of the names given; -1 if none is. */
    private static int findColumn(List<String> header, String... names) {
        for (int i = 0; i < header.size(); i++) {
            String name = header.get(i).replaceAll("\\s", "").toLowerCase(Locale.ROOT);
            for (String wanted : names) {
                if (name.equals(wanted)) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static String cell(List<String> cells, int column) {
        return column < cells.size() ? cells.get(column).trim() : "";
    }

    private static BigDecimal weight(String text, int rowNumber) {
        String number = text.endsWith("%") ? text.substring(0, text.length() - 1).trim() : text;
        if (number.isEmpty()) {
            return null;
        }
        try {
            return new BigDecimal(number);
        } catch (NumberFormatException e) {
            throw ApiException.badRequest("Row " + rowNumber + ": the weight \"" + text + "\" is not a number.");
        }
    }

    /** Splits one line on commas, keeping commas inside "double quotes". Drops a byte-order mark at the start. */
    private static List<String> split(String line) {
        if (line.startsWith("﻿")) {
            line = line.substring(1);
        }
        List<String> cells = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    cell.append('"');
                    i++;
                } else {
                    quoted = !quoted;
                }
            } else if (c == ',' && !quoted) {
                cells.add(cell.toString());
                cell.setLength(0);
            } else {
                cell.append(c);
            }
        }
        cells.add(cell.toString());
        return cells;
    }
}
