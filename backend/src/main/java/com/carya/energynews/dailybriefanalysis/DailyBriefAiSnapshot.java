package com.carya.energynews.dailybriefanalysis;

import java.time.Instant;
import java.util.List;

public record DailyBriefAiSnapshot(
        Long dailyBriefId,
        Instant dailyBriefUpdatedAt,
        List<Long> articleIds,
        DailyBriefAiRequest request
) {

    public DailyBriefAiSnapshot {
        articleIds = List.copyOf(articleIds);
    }
}
