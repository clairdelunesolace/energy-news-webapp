package com.carya.energynews.watchlistdiscovery;

import java.util.List;

public record WatchlistDiscoveryRunResponse(
        Long watchlistId,
        String watchlistName,
        int keywordsProcessed,
        int keywordsFailed,
        int discovered,
        int relevanceRejected,
        int saved,
        int duplicates,
        int keywordMatchesCreated,
        int keywordMatchesExisting,
        int skippedUnsupportedLanguage,
        int skippedInvalidUrl,
        int postProcessingAttempted,
        int metadataTranslationSucceeded,
        int metadataTranslationFailed,
        int contentExtractionSucceeded,
        int contentExtractionFailed,
        int contentTranslationSucceeded,
        int contentTranslationFailed,
        List<WatchlistDiscoveryKeywordFailure> failedKeywords,
        List<WatchlistDiscoveryKeywordResult> keywordResults
) {
}
