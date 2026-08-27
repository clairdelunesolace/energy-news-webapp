package com.carya.energynews.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.carya.energynews.article.ArticleController;
import com.carya.energynews.article.ArticlePageResponse;
import com.carya.energynews.article.ArticleService;
import com.carya.energynews.health.HealthController;
import com.carya.energynews.source.SourceController;
import com.carya.energynews.source.SourceRepository;
import com.carya.energynews.source.SourceService;
import com.carya.energynews.sync.NewsSyncController;
import com.carya.energynews.sync.NewsSyncResult;
import com.carya.energynews.sync.NewsSyncService;
import com.carya.energynews.watchlist.KeywordController;
import com.carya.energynews.watchlist.KeywordRepository;
import com.carya.energynews.watchlist.WatchlistController;
import com.carya.energynews.watchlist.WatchlistRepository;
import com.carya.energynews.watchlist.WatchlistResponse;
import com.carya.energynews.watchlist.WatchlistService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({
        AuthController.class,
        HealthController.class,
        ArticleController.class,
        SourceController.class,
        NewsSyncController.class,
        WatchlistController.class,
        KeywordController.class
})
@Import(SecurityConfig.class)
@TestPropertySource(properties = {
        "app.security.admin.username=configured-admin",
        "app.security.admin.password=test-password"
})
class SecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private FilterChainProxy filterChainProxy;

    @MockitoBean
    private ArticleService articleService;

    @MockitoBean
    private SourceService sourceService;

    @MockitoBean
    private NewsSyncService newsSyncService;

    @MockitoBean
    private SourceRepository sourceRepository;

    @MockitoBean
    private WatchlistService watchlistService;

    @MockitoBean
    private WatchlistRepository watchlistRepository;

    @MockitoBean
    private KeywordRepository keywordRepository;

    @Test
    void applicationFilterChainUsesCookieCsrfRepository() {
        List<CsrfFilter> csrfFilters = filterChainProxy.getFilterChains().stream()
                .flatMap(chain -> chain.getFilters().stream())
                .filter(CsrfFilter.class::isInstance)
                .map(CsrfFilter.class::cast)
                .toList();

        assertThat(csrfFilters).hasSize(1);
        assertThat(ReflectionTestUtils.getField(csrfFilters.getFirst(), "tokenRepository"))
                .isInstanceOf(CookieCsrfTokenRepository.class);
    }

    @Test
    void healthAndCsrfEndpointsRemainPublic() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"));

        mockMvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.headerName").value("X-XSRF-TOKEN"))
                .andExpect(cookie().exists("XSRF-TOKEN"))
                .andExpect(cookie().httpOnly("XSRF-TOKEN", true));
    }

    @Test
    void unauthenticatedReadEndpointsReturnUnauthorized() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/articles"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/sources"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/watchlists"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/news-sync"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/watchlists"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void correctPasswordEstablishesSessionThatCanAccessProtectedEndpoints() throws Exception {
        when(articleService.getAll(anyInt(), anyInt(), isNull(), isNull()))
                .thenReturn(emptyArticlePage());
        CsrfValues csrf = getCsrf(null);

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .param("username", "configured-admin")
                        .param("password", "test-password"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.username").value("configured-admin"))
                .andReturn();

        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);
        assertThat(session).isNotNull();

        mockMvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.username").value("configured-admin"));
        mockMvc.perform(get("/api/articles").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0));
    }

    @Test
    void incorrectPasswordIsRejected() throws Exception {
        CsrfValues csrf = getCsrf(null);

        mockMvc.perform(post("/api/auth/login")
                        .cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .param("username", "configured-admin")
                        .param("password", "wrong-password"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.title").value("Authentication failed"));
    }

    @Test
    void csrfIsRequiredAndValidTokenAllowsStateChangingRequest() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .param("username", "configured-admin")
                        .param("password", "test-password"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/news-sync")
                        .with(user("configured-admin")))
                .andExpect(status().isForbidden());

        CsrfValues csrf = getCsrf(null);
        when(newsSyncService.syncAllEnabledSources()).thenReturn(emptySyncResult());
        mockMvc.perform(post("/api/news-sync")
                        .with(user("configured-admin"))
                        .cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.collected").value(0));
    }

    @Test
    void watchlistMutationRequiresCsrfAndSucceedsWithValidToken() throws Exception {
        mockMvc.perform(post("/api/watchlists")
                        .with(user("configured-admin"))
                        .contentType("application/json")
                        .content("""
                                {"name":"NVIDIA"}
                                """))
                .andExpect(status().isForbidden());

        CsrfValues csrf = getCsrf(null);
        when(watchlistService.create(any())).thenReturn(new WatchlistResponse(
                1L,
                "NVIDIA",
                true,
                null,
                null,
                List.of()
        ));
        mockMvc.perform(post("/api/watchlists")
                        .with(user("configured-admin"))
                        .cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType("application/json")
                        .content("""
                                {"name":"NVIDIA"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("NVIDIA"));
    }

    @Test
    void logoutInvalidatesSessionAndSubsequentAccessIsUnauthorized() throws Exception {
        CsrfValues loginCsrf = getCsrf(null);
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .cookie(loginCsrf.cookie())
                        .header(loginCsrf.headerName(), loginCsrf.token())
                        .param("username", "configured-admin")
                        .param("password", "test-password"))
                .andExpect(status().isOk())
                .andReturn();
        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);
        assertThat(session).isNotNull();

        CsrfValues logoutCsrf = getCsrf(session);
        mockMvc.perform(post("/api/auth/logout")
                        .session(session)
                        .cookie(logoutCsrf.cookie())
                        .header(logoutCsrf.headerName(), logoutCsrf.token()))
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge("JSESSIONID", 0));

        assertThat(session.isInvalid()).isTrue();
        mockMvc.perform(get("/api/articles"))
                .andExpect(status().isUnauthorized());
    }

    private ArticlePageResponse emptyArticlePage() {
        return new ArticlePageResponse(List.of(), 0, 20, 0, 0, true, true);
    }

    private NewsSyncResult emptySyncResult() {
        return new NewsSyncResult(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
    }

    private CsrfValues getCsrf(MockHttpSession session) throws Exception {
        MockHttpServletRequestBuilder request = get("/api/auth/csrf");
        if (session != null) {
            request.session(session);
        }

        MvcResult result = mockMvc.perform(request)
                .andExpect(status().isOk())
                .andExpect(cookie().exists("XSRF-TOKEN"))
                .andReturn();
        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        Cookie cookie = result.getResponse().getCookie("XSRF-TOKEN");
        assertThat(cookie).isNotNull();
        return new CsrfValues(
                response.get("token").asText(),
                response.get("headerName").asText(),
                cookie
        );
    }

    private record CsrfValues(String token, String headerName, Cookie cookie) {
    }
}
