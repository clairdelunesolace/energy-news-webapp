package com.carya.energynews.dailybriefanalysis;

import java.util.List;

public record DailyBriefAiEvent(
        String title,
        String summary,
        String whyItMatters,
        List<Long> supportingArticleIds
) {

    public DailyBriefAiEvent {
        supportingArticleIds = supportingArticleIds == null
                ? null
                : List.copyOf(supportingArticleIds);
    }
}
