package com.carya.energynews.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.security.admin")
public record AdminCredentialsProperties(
        String username,
        String password
) {
}
