package com.carya.energynews.watchlist;

import java.time.Instant;

public record KeywordResponse(
        Long id,
        String keyword,
        boolean enabled,
        Instant createdAt,
        Instant updatedAt
) {
}
