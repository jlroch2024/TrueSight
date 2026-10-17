package com.truesight.news;

import com.truesight.support.Examples;
import org.junit.jupiter.api.Test;

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
}
