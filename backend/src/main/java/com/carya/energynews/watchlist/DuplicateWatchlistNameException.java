package com.carya.energynews.watchlist;

public class DuplicateWatchlistNameException extends RuntimeException {

    public DuplicateWatchlistNameException(String name) {
        super("A watchlist named '" + name + "' already exists");
    }

    public DuplicateWatchlistNameException(String name, Throwable cause) {
        super("A watchlist named '" + name + "' already exists", cause);
    }
}
