package com.carya.energynews.dailybriefanalysis;

import java.util.List;

public record DailyBriefAiResult(
        String headline,
        String overview,
        List<DailyBriefAiEvent> events
) {

    public DailyBriefAiResult {
        events = events == null ? null : List.copyOf(events);
    }
}
