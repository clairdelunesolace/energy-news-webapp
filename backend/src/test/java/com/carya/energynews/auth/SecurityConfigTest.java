package com.carya.energynews.auth;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecurityConfigTest {

    private final SecurityConfig securityConfig = new SecurityConfig();

    @Test
    void createsBcryptBackedAdminFromConfiguredCredentials() {
        PasswordEncoder passwordEncoder = securityConfig.passwordEncoder();
        UserDetailsService userDetailsService = securityConfig.userDetailsService(
                new AdminCredentialsProperties("configured-admin", "test-password"),
                passwordEncoder
        );

        UserDetails admin = userDetailsService.loadUserByUsername("configured-admin");

        assertThat(admin.getPassword()).isNotEqualTo("test-password");
        assertThat(passwordEncoder.matches("test-password", admin.getPassword())).isTrue();
        assertThat(passwordEncoder.matches("wrong-password", admin.getPassword())).isFalse();
    }

    @Test
    void rejectsBlankAdminPassword() {
        assertThatThrownBy(() -> securityConfig.userDetailsService(
                new AdminCredentialsProperties("admin", " "),
                securityConfig.passwordEncoder()
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("ADMIN_PASSWORD must be configured and must not be blank");
    }
}
