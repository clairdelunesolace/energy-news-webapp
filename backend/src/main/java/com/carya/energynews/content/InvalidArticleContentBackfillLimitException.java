package com.carya.energynews.content;

public class InvalidArticleContentBackfillLimitException extends RuntimeException {

    public InvalidArticleContentBackfillLimitException() {
        super("Article content backfill limit must be between 1 and 20");
    }
}
