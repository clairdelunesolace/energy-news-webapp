package com.carya.energynews.watchlistdiscovery;

import com.carya.energynews.auth.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(WatchlistDiscoveryController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = {
        "app.security.admin.username=configured-admin",
        "app.security.admin.password=test-password"
})
class WatchlistDiscoverySecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private WatchlistDiscoveryService discoveryService;

    @Test
    void anonymousPostIsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/watchlist-discovery/run")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"watchlistId\":1}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedPostWithoutCsrfIsForbidden() throws Exception {
        mockMvc.perform(post("/api/watchlist-discovery/run")
                        .with(user("configured-admin"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"watchlistId\":1}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void authenticatedPostWithCsrfSucceeds() throws Exception {
        when(discoveryService.run(any(WatchlistDiscoveryRunRequest.class)))
                .thenReturn(new WatchlistDiscoveryRunResponse(
                        1L,
                        "Industry Test",
                        0,
                        0,
                        0,
                        0,
                        0,
                        0,
                        0,
                        0,
                        0,
                        0,
                        List.of(),
                        List.of()
                ));

        mockMvc.perform(post("/api/watchlist-discovery/run")
                        .with(user("configured-admin"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"watchlistId\":1}"))
                .andExpect(status().isOk());
    }
}
