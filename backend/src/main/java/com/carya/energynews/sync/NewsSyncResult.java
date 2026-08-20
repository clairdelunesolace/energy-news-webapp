package com.carya.energynews.sync;

public record NewsSyncResult(
        int collected,
        int filteredOut,
        int saved,
        int duplicates,
        int translated,
        int translationFailed,
        int failedSources
) {
}
