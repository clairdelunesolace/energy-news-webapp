package com.carya.energynews.watchlist;

import java.time.Instant;
import java.util.List;

public record WatchlistResponse(
        Long id,
        String name,
        boolean enabled,
        Instant createdAt,
        Instant updatedAt,
        List<KeywordResponse> keywords
) {
}
