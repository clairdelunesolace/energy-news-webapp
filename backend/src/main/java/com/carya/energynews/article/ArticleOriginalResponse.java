package com.carya.energynews.article;

import com.carya.energynews.source.SourceLanguage;

public record ArticleOriginalResponse(
        SourceLanguage language,
        String title,
        String description,
        String content
) {
}
