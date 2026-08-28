package com.carya.energynews.dailybrief;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record DailyBriefResponse(
        Long id,
        Long watchlistId,
        String watchlistName,
        LocalDate briefDate,
        String zone,
        Instant windowStart,
        Instant windowEnd,
        int candidateCount,
        int itemCount,
        Instant createdAt,
        Instant updatedAt,
        List<DailyBriefItemResponse> items
) {
}
