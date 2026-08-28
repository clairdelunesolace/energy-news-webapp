package com.carya.energynews.watchlistdiscovery;

import com.carya.energynews.article.Article;

public record WatchlistDiscoveryPersistenceResult(
        Status status,
        Article article,
        boolean keywordMatchCreated
) {

    static WatchlistDiscoveryPersistenceResult unsupportedLanguage() {
        return new WatchlistDiscoveryPersistenceResult(
                Status.SKIPPED_UNSUPPORTED_LANGUAGE,
                null,
                false
        );
    }

    public Long articleId() {
        return article == null ? null : article.getId();
    }

    public enum Status {
        SAVED,
        DUPLICATE,
        SKIPPED_UNSUPPORTED_LANGUAGE
    }
}
