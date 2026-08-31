package com.carya.energynews.dailybriefanalysis;

import java.time.Instant;
import java.util.List;

public record DailyBriefAiArticle(
        Long articleId,
        String title,
        String description,
        String sourceName,
        Instant publishedAt,
        Instant effectiveTime,
        List<String> matchedKeywords
) {

    public DailyBriefAiArticle {
        matchedKeywords = matchedKeywords == null ? List.of() : List.copyOf(matchedKeywords);
    }
}
