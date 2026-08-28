package com.carya.energynews.watchlistdiscovery;

final class WatchlistDiscoveryExecutionException extends RuntimeException {

    private final WatchlistDiscoveryExecutionResult partialResult;

    WatchlistDiscoveryExecutionException(
            RuntimeException cause,
            WatchlistDiscoveryExecutionResult partialResult
    ) {
        super("Watchlist discovery failed unexpectedly", cause);
        this.partialResult = partialResult;
    }

    WatchlistDiscoveryExecutionResult partialResult() {
        return partialResult;
    }
}
