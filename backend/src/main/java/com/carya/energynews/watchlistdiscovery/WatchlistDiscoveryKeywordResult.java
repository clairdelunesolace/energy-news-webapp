package com.carya.energynews.watchlistdiscovery;

public record WatchlistDiscoveryKeywordResult(
        Long keywordId,
        String keyword,
        int discovered,
        int relevanceRejected,
        int saved,
        int duplicates,
        int keywordMatchesCreated,
        int keywordMatchesExisting,
        int skippedUnsupportedLanguage,
        int skippedInvalidUrl,
        String failure
) {
}
