package com.carya.energynews.dailybriefanalysis;

import java.time.LocalDate;
import java.util.List;

public record DailyBriefAiRequest(
        String watchlistName,
        LocalDate briefDate,
        String zone,
        List<DailyBriefAiArticle> articles
) {

    public DailyBriefAiRequest {
        articles = articles == null ? List.of() : List.copyOf(articles);
    }
}
