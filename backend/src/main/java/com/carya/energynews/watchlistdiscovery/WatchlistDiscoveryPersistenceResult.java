package com.carya.energynews.watchlistdiscovery;

public record WatchlistDiscoveryPersistenceResult(
        Status status,
        Long articleId,
        boolean keywordMatchCreated
) {

    static WatchlistDiscoveryPersistenceResult unsupportedLanguage() {
        return new WatchlistDiscoveryPersistenceResult(
                Status.SKIPPED_UNSUPPORTED_LANGUAGE,
                null,
                false
        );
    }

    public enum Status {
        SAVED,
        DUPLICATE,
        SKIPPED_UNSUPPORTED_LANGUAGE
    }
}
