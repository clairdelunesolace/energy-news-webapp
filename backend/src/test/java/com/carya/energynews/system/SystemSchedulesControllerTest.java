package com.carya.energynews.system;

import com.carya.energynews.auth.SecurityConfig;
import com.carya.energynews.dailybrief.DailyBriefSchedulerProperties;
import com.carya.energynews.watchlistdiscovery.WatchlistDiscoverySchedulerProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SystemSchedulesController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = {
        "app.security.admin.username=configured-admin",
        "app.security.admin.password=test-password"
})
class SystemSchedulesControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private WatchlistDiscoverySchedulerProperties newsDiscovery;

    @MockitoBean
    private DailyBriefSchedulerProperties dailyBrief;

    @Test
    void anonymousReadRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/system/schedules"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(newsDiscovery, dailyBrief);
    }

    @Test
    void authenticatedReadReturnsOnlySafeScheduleFieldsWithoutRequiringCsrf() throws Exception {
        when(newsDiscovery.enabled()).thenReturn(true);
        when(newsDiscovery.cron()).thenReturn("0 25 7 * * *");
        when(newsDiscovery.zone()).thenReturn("UTC");
        when(dailyBrief.enabled()).thenReturn(false);
        when(dailyBrief.cron()).thenReturn("0 45 9 * * *");
        when(dailyBrief.zone()).thenReturn("Asia/Shanghai");

        mockMvc.perform(get("/api/system/schedules")
                        .with(user("configured-admin").authorities(List.of())))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {
                          "newsDiscovery": {"enabled":true,"cron":"0 25 7 * * *","zone":"UTC","dailyTime":"07:25"},
                          "dailyBrief": {"enabled":false,"cron":"0 45 9 * * *","zone":"Asia/Shanghai","dailyTime":"09:45"}
                        }
                        """, JsonCompareMode.STRICT));
    }

    @Test
    void unsupportedCronIsSerializedWithNullDailyTime() throws Exception {
        when(newsDiscovery.cron()).thenReturn("0 */10 * * * *");
        when(newsDiscovery.zone()).thenReturn("UTC");
        when(dailyBrief.cron()).thenReturn("@daily");
        when(dailyBrief.zone()).thenReturn("UTC");

        mockMvc.perform(get("/api/system/schedules")
                        .with(user("configured-admin").authorities(List.of())))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {
                          "newsDiscovery": {"enabled":false,"cron":"0 */10 * * * *","zone":"UTC","dailyTime":null},
                          "dailyBrief": {"enabled":false,"cron":"@daily","zone":"UTC","dailyTime":null}
                        }
                        """, JsonCompareMode.STRICT));
    }

    @Test
    void endpointDoesNotAllowEditingSchedules() throws Exception {
        mockMvc.perform(post("/api/system/schedules")
                        .with(user("configured-admin").authorities(List.of()))
                        .with(csrf()))
                .andExpect(status().isMethodNotAllowed());
        verifyNoInteractions(newsDiscovery, dailyBrief);
    }
}
