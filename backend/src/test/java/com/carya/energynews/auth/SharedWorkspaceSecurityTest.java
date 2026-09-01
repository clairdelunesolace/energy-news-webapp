package com.carya.energynews.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "app.security.admin.username=shared-test-account",
        "app.security.admin.password=test-password",
        "app.discovery.provider=none",
        "app.discovery.scheduler.enabled=false",
        "app.news-sync.cron=-",
        "app.daily-brief.scheduler.enabled=false",
        "app.daily-brief.ai.provider=none",
        "spring.datasource.url=jdbc:h2:mem:shared-workspace;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.open-in-view=false"
})
@AutoConfigureMockMvc
class SharedWorkspaceSecurityTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void independentRolelessSessionsShareCommittedKeywordsAndLogoutIsSessionLocal() throws Exception {
        MockHttpSession first = login();
        MockHttpSession second = login();
        assertThat(first.getId()).isNotEqualTo(second.getId());
        for (MockHttpSession session : List.of(first, second)) {
            SecurityContext context = (SecurityContext) session.getAttribute(
                    HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
            assertThat(context.getAuthentication().getAuthorities()).isEmpty();
            assertThat(context.getAuthentication().getCredentials()).isNull();
        }

        MvcResult created = mockMvc.perform(withCsrf(post("/api/watchlists"), first)
                        .contentType("application/json").content("{\"name\":\"Shared workspace fixture\"}"))
                .andExpect(status().isCreated()).andReturn();
        long watchlistId = json(created).path("id").asLong();
        MvcResult keyword = mockMvc.perform(withCsrf(post("/api/watchlists/" + watchlistId + "/keywords"), first)
                        .contentType("application/json").content("{\"keyword\":\"shared fixture keyword\"}"))
                .andExpect(status().isCreated()).andReturn();
        long keywordId = json(keyword).path("id").asLong();

        mockMvc.perform(get("/api/watchlists/" + watchlistId).session(second))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keywords[0].id").value(keywordId))
                .andExpect(jsonPath("$.keywords[0].enabled").value(true));

        mockMvc.perform(patch("/api/keywords/" + keywordId).session(second)
                        .contentType("application/json").content("{\"enabled\":false}"))
                .andExpect(status().isForbidden());
        MvcResult csrfForSecond = mockMvc.perform(get("/api/auth/csrf").session(second)).andReturn();
        mockMvc.perform(patch("/api/keywords/" + keywordId).session(second)
                        .cookie(csrfForSecond.getResponse().getCookie("XSRF-TOKEN"))
                        .header("X-XSRF-TOKEN", "invalid-token")
                        .contentType("application/json").content("{\"enabled\":false}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/watchlists/" + watchlistId).session(first))
                .andExpect(jsonPath("$.keywords[0].enabled").value(true));

        mockMvc.perform(withCsrf(patch("/api/keywords/" + keywordId), second)
                        .contentType("application/json").content("{\"enabled\":false}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/watchlists/" + watchlistId).session(first))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keywords[0].enabled").value(false));

        mockMvc.perform(delete("/api/keywords/" + keywordId).session(first))
                .andExpect(status().isForbidden());
        mockMvc.perform(withCsrf(delete("/api/keywords/" + keywordId), first))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/watchlists/" + watchlistId).session(second))
                .andExpect(status().isOk()).andExpect(jsonPath("$.keywords").isEmpty());

        mockMvc.perform(post("/api/auth/logout").session(first)).andExpect(status().isForbidden());
        assertThat(first.isInvalid()).isFalse();
        mockMvc.perform(withCsrf(post("/api/auth/logout"), first)).andExpect(status().isNoContent());
        assertThat(first.isInvalid()).isTrue();
        mockMvc.perform(get("/api/watchlists/" + watchlistId)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/watchlists/" + watchlistId).session(second)).andExpect(status().isOk());
        mockMvc.perform(get("/api/watchlists/" + watchlistId).session(login()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.keywords").isEmpty());
        mockMvc.perform(withCsrf(delete("/api/watchlists/" + watchlistId), second))
                .andExpect(status().isNoContent());
    }

    private MockHttpSession login() throws Exception {
        MvcResult result = mockMvc.perform(withCsrf(post("/api/auth/login"), new MockHttpSession())
                        .param("username", "shared-test-account").param("password", "test-password"))
                .andExpect(status().isOk()).andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private MockHttpServletRequestBuilder withCsrf(
            MockHttpServletRequestBuilder request, MockHttpSession session
    ) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/auth/csrf").session(session))
                .andExpect(status().isOk()).andReturn();
        JsonNode csrf = json(result);
        Cookie cookie = result.getResponse().getCookie("XSRF-TOKEN");
        return request.session(session).cookie(cookie)
                .header(csrf.path("headerName").asText(), csrf.path("token").asText());
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}
