package com.carya.energynews.source;

public class DuplicateSourceUrlException extends RuntimeException {

    public DuplicateSourceUrlException(String url) {
        super("A source with URL '" + url + "' already exists");
    }

    public DuplicateSourceUrlException(String url, Throwable cause) {
        super("A source with URL '" + url + "' already exists", cause);
    }
}
