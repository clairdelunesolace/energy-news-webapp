package com.carya.energynews.watchlistdiscovery;

record WatchlistDiscoveryExecutionResult(
        WatchlistDiscoveryRunResponse response,
        int keywordsSkippedByRequestLimit
) {
}
