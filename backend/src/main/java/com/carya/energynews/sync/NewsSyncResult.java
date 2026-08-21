package com.carya.energynews.sync;

public record NewsSyncResult(
        int collected,
        int filteredOut,
        int saved,
        int duplicates,
        int translated,
        int translationFailed,
        int contentFetched,
        int contentFetchFailed,
        int contentTranslated,
        int contentTranslationFailed,
        int failedSources
) {

    public NewsSyncResult(
            int collected,
            int filteredOut,
            int saved,
            int duplicates,
            int translated,
            int translationFailed,
            int failedSources
    ) {
        this(
                collected,
                filteredOut,
                saved,
                duplicates,
                translated,
                translationFailed,
                0,
                0,
                0,
                0,
                failedSources
        );
    }
}
