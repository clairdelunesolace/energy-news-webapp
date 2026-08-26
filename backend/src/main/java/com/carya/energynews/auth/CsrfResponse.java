package com.carya.energynews.auth;

public record CsrfResponse(
        String token,
        String headerName
) {
}
