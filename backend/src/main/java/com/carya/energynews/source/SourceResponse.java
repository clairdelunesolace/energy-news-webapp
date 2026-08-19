package com.carya.energynews.source;

import java.time.Instant;

public record SourceResponse(
        Long id,
        String name,
        String url,
        SourceType type,
        SourcePriority priority,
        boolean enabled,
        Instant createdAt,
        Instant updatedAt
) {
}
