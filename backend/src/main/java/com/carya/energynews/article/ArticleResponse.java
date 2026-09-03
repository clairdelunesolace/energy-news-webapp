package com.carya.energynews.article;

import java.time.Instant;
import java.util.List;

public record ArticleResponse(
        Long id,
        ArticleSourceResponse source,
        String url,
        Instant publishedAt,
        Instant collectedAt,
        ArticleOriginalResponse original,
        ArticleTranslationResponse translation,
        Instant createdAt,
        Instant updatedAt,
        List<String> tags
) {
}
