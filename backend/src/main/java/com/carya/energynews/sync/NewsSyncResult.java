package com.carya.energynews.sync;

public record NewsSyncResult(
        int collected,
        int saved,
        int duplicates,
        int failedSources
) {
}
