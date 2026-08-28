package com.carya.energynews.article;

public record ArticlePostProcessingResult(
        boolean metadataTranslationSucceeded,
        boolean metadataTranslationFailed,
        boolean contentExtractionSucceeded,
        boolean contentExtractionFailed,
        boolean contentTranslationSucceeded,
        boolean contentTranslationFailed
) {
}
