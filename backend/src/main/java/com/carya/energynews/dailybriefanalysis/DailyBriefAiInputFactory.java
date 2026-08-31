package com.carya.energynews.dailybriefanalysis;

import com.carya.energynews.dailybrief.DailyBriefItemResponse;
import com.carya.energynews.dailybrief.DailyBriefResponse;
import org.springframework.stereotype.Component;

@Component
public class DailyBriefAiInputFactory {

    static final int MAX_DESCRIPTION_CODE_POINTS = 2_000;

    public DailyBriefAiRequest create(DailyBriefResponse brief) {
        return new DailyBriefAiRequest(
                brief.watchlistName(),
                brief.briefDate(),
                brief.zone(),
                brief.items().stream().map(DailyBriefAiInputFactory::toArticle).toList()
        );
    }

    private static DailyBriefAiArticle toArticle(DailyBriefItemResponse item) {
        return new DailyBriefAiArticle(
                item.articleId(),
                item.title(),
                truncate(item.description()),
                item.sourceName(),
                item.publishedAt(),
                item.effectiveTime(),
                item.matchedKeywords()
        );
    }

    private static String truncate(String value) {
        if (value == null
                || value.codePointCount(0, value.length()) <= MAX_DESCRIPTION_CODE_POINTS) {
            return value;
        }
        int end = value.offsetByCodePoints(0, MAX_DESCRIPTION_CODE_POINTS);
        return value.substring(0, end);
    }
}
