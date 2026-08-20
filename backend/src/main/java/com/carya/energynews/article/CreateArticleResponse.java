package com.carya.energynews.article;

import java.time.Instant;

public record CreateArticleResponse(
        Long id,
        String title,
        String url,
        String description,
        String content,
        Instant publishedAt,
        Instant collectedAt,
        Long sourceId,
        String sourceName,
        Instant createdAt,
        Instant updatedAt
) {
}
