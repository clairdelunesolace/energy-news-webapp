package com.carya.energynews.dailybrief;

import java.time.Instant;
import java.time.LocalDate;

public record ScheduledDailyBriefResult(
        Instant runAt,
        LocalDate briefDate,
        int watchlistsProcessed,
        int watchlistsFailed,
        int briefsGenerated,
        int emptyBriefs,
        int aiGenerated,
        int aiSkipped,
        int aiFailed,
        boolean skippedOverlap,
        boolean schedulerFailed
) {
}
