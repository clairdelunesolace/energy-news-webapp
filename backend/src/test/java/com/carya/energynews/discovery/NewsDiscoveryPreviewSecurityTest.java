package com.carya.energynews.discovery;

import com.carya.energynews.auth.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NewsDiscoveryPreviewController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = {
        "app.security.admin.username=configured-admin",
        "app.security.admin.password=test-password"
})
class NewsDiscoveryPreviewSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NewsDiscoveryService discoveryService;

    @MockitoBean
    private Clock clock;

    @Test
    void anonymousPreviewIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/discovery/preview")
                        .param("keyword", "BESS"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedPreviewIsAllowedWhenProviderIsConfigured() throws Exception {
        when(discoveryService.providerName()).thenReturn("brave-news");
        when(discoveryService.discover(any())).thenReturn(List.of());

        mockMvc.perform(get("/api/discovery/preview")
                        .with(user("configured-admin"))
                        .param("keyword", "BESS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provider").value("brave-news"))
                .andExpect(jsonPath("$.count").value(0));
    }
}
