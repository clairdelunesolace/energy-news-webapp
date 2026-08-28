package com.carya.energynews.watchlistdiscovery;

@FunctionalInterface
public interface DiscoveryRequestPacer {

    void awaitNextRequest();

    static DiscoveryRequestPacer noDelay() {
        return () -> {
        };
    }
}
