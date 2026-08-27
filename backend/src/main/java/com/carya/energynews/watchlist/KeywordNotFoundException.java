package com.carya.energynews.watchlist;

public class KeywordNotFoundException extends RuntimeException {

    public KeywordNotFoundException(Long id) {
        super("Keyword with id " + id + " was not found");
    }
}
