package com.carya.energynews.translation;

import com.carya.energynews.source.SourceLanguage;

public record TranslationInput(
        SourceLanguage sourceLanguage,
        TranslationLanguage targetLanguage,
        String title,
        String description,
        String content
) {

    public TranslationInput(
            SourceLanguage sourceLanguage,
            TranslationLanguage targetLanguage,
            String title,
            String description
    ) {
        this(sourceLanguage, targetLanguage, title, description, null);
    }
}
