package com.carya.energynews.dailybriefanalysis;

public class DailyBriefAiException extends RuntimeException {

    public enum Failure {
        RATE_LIMITED,
        AUTHENTICATION,
        AUTHORIZATION,
        INVALID_REQUEST,
        TIMEOUT,
        UPSTREAM,
        MALFORMED_RESPONSE
    }

    private final Failure failure;

    public DailyBriefAiException(Failure failure, String message) {
        super(message);
        this.failure = failure;
    }

    public DailyBriefAiException(Failure failure, String message, Throwable cause) {
        super(message, cause);
        this.failure = failure;
    }

    public Failure getFailure() {
        return failure;
    }
}
