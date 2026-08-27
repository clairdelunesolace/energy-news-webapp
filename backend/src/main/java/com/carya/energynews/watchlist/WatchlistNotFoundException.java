package com.carya.energynews.watchlist;

public class WatchlistNotFoundException extends RuntimeException {

    public WatchlistNotFoundException(Long id) {
        super("Watchlist with id " + id + " was not found");
    }
}
