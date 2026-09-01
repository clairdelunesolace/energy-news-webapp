package com.carya.energynews.dailybrief;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DailyBriefController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = {
        "app.security.admin.username=configured-admin",
        "app.security.admin.password=test-password"
})
class DailyBriefSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DailyBriefService dailyBriefService;

    @Test
    void anonymousRequestsAreUnauthorized() throws Exception {
        mockMvc.perform(get("/api/daily-briefs/1"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/daily-briefs/generate")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"watchlistId\":1}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedPostRequiresCsrf() throws Exception {
        mockMvc.perform(post("/api/daily-briefs/generate")
                        .with(user("configured-admin").authorities(List.of()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"watchlistId\":1}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void authenticatedAccountWithoutRolesSucceedsWithRequiredCsrf() throws Exception {
        when(dailyBriefService.generate(any(GenerateDailyBriefRequest.class)))
                .thenReturn(DailyBriefControllerTest.response());
        when(dailyBriefService.getById(1L)).thenReturn(DailyBriefControllerTest.response());

        mockMvc.perform(post("/api/daily-briefs/generate")
                        .with(user("configured-admin").authorities(List.of()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"watchlistId\":1}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/daily-briefs/1")
                        .with(user("configured-admin").authorities(List.of())))
                .andExpect(status().isOk());
    }
}
