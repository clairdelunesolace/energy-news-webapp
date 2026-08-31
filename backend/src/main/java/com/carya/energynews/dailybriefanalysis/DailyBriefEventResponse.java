package com.carya.energynews.dailybriefanalysis;

import java.util.List;

public record DailyBriefEventResponse(
        int rank,
        String title,
        String summary,
        String whyItMatters,
        List<Long> supportingArticleIds
) {
}
