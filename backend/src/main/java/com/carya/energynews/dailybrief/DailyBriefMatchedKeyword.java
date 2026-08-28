package com.carya.energynews.dailybrief;

public record DailyBriefMatchedKeyword(
        Long articleId,
        Long keywordId,
        String keyword
) {
}
