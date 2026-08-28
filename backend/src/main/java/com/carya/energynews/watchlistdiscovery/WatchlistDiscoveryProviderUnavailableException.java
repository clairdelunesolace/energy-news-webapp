package com.carya.energynews.watchlistdiscovery;

public class WatchlistDiscoveryProviderUnavailableException extends RuntimeException {

    public WatchlistDiscoveryProviderUnavailableException() {
        super("News discovery provider is not configured.");
    }
}
