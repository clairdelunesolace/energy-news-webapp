package com.carya.energynews.translation;

public class InvalidArticleContentTranslationBackfillLimitException extends RuntimeException {

    public InvalidArticleContentTranslationBackfillLimitException() {
        super("Article content translation backfill limit must be between 1 and 10");
    }
}
