package com.carya.energynews.discovery;

import java.time.Instant;

public record NewsDiscoveryQuery(
        String keyword,
        Instant from,
        Instant to,
        int limit
) {

    public static final int MAX_LIMIT = 100;

    public NewsDiscoveryQuery {
        if (keyword == null) {
            throw new IllegalArgumentException("Discovery keyword is required");
        }
        keyword = keyword.strip();
        if (keyword.isBlank()) {
            throw new IllegalArgumentException("Discovery keyword must not be blank");
        }
        if (from != null && to != null && from.isAfter(to)) {
            throw new IllegalArgumentException("Discovery start time must not be after end time");
        }
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new IllegalArgumentException(
                    "Discovery limit must be between 1 and " + MAX_LIMIT
            );
        }
    }
}
