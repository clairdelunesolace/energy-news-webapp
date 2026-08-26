package com.carya.energynews.auth;

public record AuthResponse(
        boolean authenticated,
        String username
) {
}
