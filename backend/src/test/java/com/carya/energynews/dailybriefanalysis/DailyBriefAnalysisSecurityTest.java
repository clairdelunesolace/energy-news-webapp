package com.carya.energynews.dailybriefanalysis;

import com.carya.energynews.auth.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DailyBriefAnalysisController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = {
        "app.security.admin.username=configured-admin",
        "app.security.admin.password=test-password"
})
class DailyBriefAnalysisSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DailyBriefAnalysisService analysisService;

    @Test
    void anonymousPostAndGetAreUnauthorized() throws Exception {
        mockMvc.perform(post("/api/daily-briefs/1/analysis/generate").with(csrf()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/daily-briefs/1/analysis"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedPostRequiresCsrf() throws Exception {
        mockMvc.perform(post("/api/daily-briefs/1/analysis/generate")
                        .with(user("configured-admin").authorities(List.of())))
                .andExpect(status().isForbidden());
    }

    @Test
    void authenticatedAccountWithoutRolesSucceedsWithRequiredCsrf() throws Exception {
        when(analysisService.generate(1L)).thenReturn(DailyBriefAnalysisControllerTest.response());
        when(analysisService.get(1L)).thenReturn(DailyBriefAnalysisControllerTest.response());

        mockMvc.perform(post("/api/daily-briefs/1/analysis/generate")
                        .with(user("configured-admin").authorities(List.of()))
                        .with(csrf()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/daily-briefs/1/analysis")
                        .with(user("configured-admin").authorities(List.of())))
                .andExpect(status().isOk());
    }
}
