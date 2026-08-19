package com.carya.energynews.collection;

import java.time.Instant;

public record CollectedArticle(
        String title,
        String url,
        String description,
        String content,
        Instant publishedAt,
        Long sourceId
) {
}
