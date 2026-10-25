package com.truesight.news;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/** A portfolio manager's status change for every alert in one event. */
public record AlertGroupStatusRequest(@NotEmpty List<@NotNull Long> alertIds,
                                     @NotNull AlertStatus status) {
}
