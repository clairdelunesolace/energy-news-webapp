package com.carya.energynews.article;

import com.carya.energynews.translation.TranslationLanguage;

public record ArticleTranslationResponse(
        TranslationLanguage language,
        String title,
        String description,
        String content
) {

    public ArticleTranslationResponse(
            TranslationLanguage language,
            String title,
            String description
    ) {
        this(language, title, description, null);
    }
}
