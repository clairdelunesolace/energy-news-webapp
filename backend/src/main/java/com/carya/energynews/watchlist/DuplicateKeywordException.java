package com.carya.energynews.watchlist;

public class DuplicateKeywordException extends RuntimeException {

    public DuplicateKeywordException(String keyword) {
        super("Keyword '" + keyword + "' already exists in this watchlist");
    }

    public DuplicateKeywordException(String keyword, Throwable cause) {
        super("Keyword '" + keyword + "' already exists in this watchlist", cause);
    }
}
