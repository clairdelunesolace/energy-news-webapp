package com.carya.energynews.discovery;

public class NewsDiscoveryException extends RuntimeException {

    public NewsDiscoveryException(String message) {
        super(message);
    }

    public NewsDiscoveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
