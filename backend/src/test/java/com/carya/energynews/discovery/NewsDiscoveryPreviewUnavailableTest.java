package com.carya.energynews.discovery;

import com.carya.energynews.auth.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NewsDiscoveryPreviewController.class)
@Import({SecurityConfig.class, NewsDiscoveryQueryFactory.class})
@TestPropertySource(properties = {
        "app.security.admin.username=configured-admin",
        "app.security.admin.password=test-password"
})
class NewsDiscoveryPreviewUnavailableTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private Clock clock;

    @Test
    void authenticatedPreviewReturnsServiceUnavailableWithoutProvider() throws Exception {
        mockMvc.perform(get("/api/discovery/preview")
                        .with(user("configured-admin"))
                        .param("keyword", "BESS"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Service Unavailable"))
                .andExpect(jsonPath("$.detail")
                        .value("News discovery provider is not configured."));
    }

    @Test
    void anonymousPreviewIsUnauthorizedBeforeProviderAvailabilityIsRevealed() throws Exception {
        mockMvc.perform(get("/api/discovery/preview")
                        .param("keyword", "BESS"))
                .andExpect(status().isUnauthorized());
    }
}
