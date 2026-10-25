package com.truesight.news;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.truesight.support.Examples;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GoogleNewsParserTest {

    @Test
    void parsesTheSavedGoogleNewsFeedAndCleansItsTitles() {
        List<NewsItem> items = new GoogleNewsParser().parse(Examples.googleNewsTsmc());

        assertThat(items).hasSize(71);
        assertThat(items.getFirst().title())
                .isEqualTo("Taiwan Semiconductor Manufacturing Just Dropped a Major Bombshell Investors Must Be Aware Of");
        assertThat(items.getFirst().source()).isEqualTo("Yahoo Finance");
        assertThat(items.getFirst().publishedAt()).isEqualTo(Instant.parse("2026-10-12T17:07:00Z"));
        assertThat(items).allSatisfy(item -> {
            assertThat(item.title()).doesNotEndWith(" - " + item.source());
            assertThat(item.publishedAt()).isNotNull();
        });
    }

    @Test
    void skipsUnreadableDatesAndLogsOnlyTheirCountOnce() {
        String xml = """
                <rss><channel>
                  <item>
                    <title>Readable item</title><link>https://example.invalid/good</link><source>Example</source>
                    <pubDate>Wed, 14 Oct 2026 13:49:30 GMT</pubDate>
                  </item>
                  <item>
                    <title>SECRET_SKIPPED_HEADLINE</title><link>https://example.invalid/bad</link><source>Example</source>
                    <pubDate>NOT_A_DATE</pubDate>
                  </item>
                  <item>
                    <title>SECOND_SECRET_HEADLINE</title><link>https://example.invalid/missing</link><source>Example</source>
                  </item>
                </channel></rss>
                """;
        Logger logger = (Logger) LoggerFactory.getLogger(GoogleNewsParser.class);
        ListAppender<ILoggingEvent> warnings = new ListAppender<>();
        warnings.start();
        logger.addAppender(warnings);
        List<NewsItem> items;
        try {
            items = new GoogleNewsParser().parse(xml);
        } finally {
            logger.detachAppender(warnings);
            warnings.stop();
        }

        assertThat(items).extracting(NewsItem::title).containsExactly("Readable item");
        assertThat(warnings.list).hasSize(1);
        ILoggingEvent warning = warnings.list.getFirst();
        assertThat(warning.getLevel()).isEqualTo(Level.WARN);
        assertThat(warning.getFormattedMessage())
                .contains("Skipped 2 Google News item(s) with an unreadable publication date.")
                .doesNotContain("SECRET_SKIPPED_HEADLINE", "SECOND_SECRET_HEADLINE", "NOT_A_DATE");
    }
}
