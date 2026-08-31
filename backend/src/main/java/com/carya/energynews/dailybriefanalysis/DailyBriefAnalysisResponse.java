package com.carya.energynews.dailybriefanalysis;

import java.time.Instant;
import java.util.List;

public record DailyBriefAnalysisResponse(
        Long id,
        Long dailyBriefId,
        String provider,
        String model,
        String headline,
        String overview,
        Instant generatedAt,
        Instant createdAt,
        Instant updatedAt,
        List<DailyBriefEventResponse> events
) {
}
