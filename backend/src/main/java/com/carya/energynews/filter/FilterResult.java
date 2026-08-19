package com.carya.energynews.filter;

public record FilterResult(
        boolean accepted,
        String reason
) {
}
