package com.carya.energynews.watchlistdiscovery;

public record WatchlistDiscoveryKeywordFailure(
        Long keywordId,
        String keyword,
        String message
) {
}
