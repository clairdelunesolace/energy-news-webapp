package com.carya.energynews.watchlistdiscovery;

public class WatchlistDisabledException extends RuntimeException {

    public WatchlistDisabledException(Long id) {
        super("Watchlist with id " + id + " is disabled");
    }
}
