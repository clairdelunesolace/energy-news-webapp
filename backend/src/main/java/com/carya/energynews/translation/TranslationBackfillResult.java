package com.carya.energynews.translation;

public record TranslationBackfillResult(
        int selected,
        int translated,
        int failed
) {
}
