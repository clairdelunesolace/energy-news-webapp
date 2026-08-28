package com.carya.energynews.dailybrief;

import java.time.Instant;
import java.util.List;

public record DailyBriefItemResponse(
        int rank,
        Long articleId,
        String title,
        String description,
        String url,
        String sourceName,
        Instant publishedAt,
        Instant effectiveTime,
        int matchingKeywordCount,
        List<String> matchedKeywords
) {
}
