package com.truesight.briefing;

import jakarta.validation.constraints.Size;

/**
 * What the website sends to make a briefing: a picture of the graph as it was on screen, as base64 text (with or
 * without the {@code data:image/png;base64,} start). It is empty when the portfolio has no graph to show. timeZone is
 * the reader's time zone (such as {@code Asia/Singapore}), for the dates; the briefing uses UTC without it.
 */
public record BriefingRequest(
        @Size(max = 16_000_000, message = "That picture of the graph is too large.") String graphPng,
        @Size(max = 64, message = "That time zone is too long.") String timeZone) {
}
