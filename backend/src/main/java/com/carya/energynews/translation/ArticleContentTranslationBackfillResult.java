package com.carya.energynews.translation;

public record ArticleContentTranslationBackfillResult(
        int selected,
        int translated,
        int failed
) {
}
