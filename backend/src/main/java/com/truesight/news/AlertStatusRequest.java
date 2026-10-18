package com.truesight.news;

import jakarta.validation.constraints.NotNull;

/** A portfolio manager's new state for an alert. */
public record AlertStatusRequest(@NotNull AlertStatus status) {
}
