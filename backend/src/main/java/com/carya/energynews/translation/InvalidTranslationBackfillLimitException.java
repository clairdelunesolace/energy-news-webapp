package com.carya.energynews.translation;

public class InvalidTranslationBackfillLimitException extends RuntimeException {

    public InvalidTranslationBackfillLimitException() {
        super("Translation backfill limit must be between 1 and 100");
    }
}
