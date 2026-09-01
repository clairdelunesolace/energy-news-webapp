package com.carya.energynews.auth;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

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

        assertThat(passwordEncoder).isInstanceOf(BCryptPasswordEncoder.class);
        assertThat(admin.getAuthorities()).isEmpty();
        assertThat(admin.getPassword()).isNotEqualTo("test-password");
        assertThat(passwordEncoder.matches("test-password", admin.getPassword())).isTrue();
        assertThat(passwordEncoder.matches("wrong-password", admin.getPassword())).isFalse();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n"})
    void rejectsBlankAdminPassword(String password) {
        assertThatThrownBy(() -> securityConfig.userDetailsService(
                new AdminCredentialsProperties("admin", password),
                securityConfig.passwordEncoder()
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("ADMIN_PASSWORD must be configured and must not be blank");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n"})
    void rejectsBlankAdminUsername(String username) {
        assertThatThrownBy(() -> securityConfig.userDetailsService(
                new AdminCredentialsProperties(username, "test-password"),
                securityConfig.passwordEncoder()
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("ADMIN_USERNAME must not be blank");
    }

    @Test
    void credentialSettingsStringNeverIncludesConfiguredValues() {
        AdminCredentialsProperties credentials = new AdminCredentialsProperties("team-fixture", "secret-fixture");

        assertThat(credentials.toString())
                .isEqualTo("AdminCredentialsProperties[username=REDACTED, password=REDACTED]")
                .doesNotContain("team-fixture", "secret-fixture");
    }
}
