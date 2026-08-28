package com.carya.energynews.dailybrief;

import java.time.Instant;

public record DailyBriefCandidate(
        Long articleId,
        long matchingKeywordCount,
        Instant effectiveTime
) {
}
