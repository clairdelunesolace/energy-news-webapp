package com.carya.energynews.source;

public class SourceNotFoundException extends RuntimeException {

    public SourceNotFoundException(Long id) {
        super("Source with id " + id + " was not found");
    }
}
