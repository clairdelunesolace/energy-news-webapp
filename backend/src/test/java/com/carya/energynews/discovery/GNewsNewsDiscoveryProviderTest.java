package com.carya.energynews.discovery;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GNewsNewsDiscoveryProviderTest {

    private static final String API_KEY = "test-gnews-api-key";

    private final Queue<TestResponse> responses = new ConcurrentLinkedQueue<>();
    private final List<RecordedRequest> requests = new ArrayList<>();

    private HttpServer server;
    private ExecutorService serverExecutor;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        serverExecutor = Executors.newCachedThreadPool(
                task -> Thread.ofPlatform().daemon().unstarted(task)
        );
        server.setExecutor(serverExecutor);
        server.createContext("/api/v4/search", this::handle);
        server.start();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
        serverExecutor.shutdownNow();
    }

    @Test
    void mapsEncodedRequestAndProviderNeutralResponse() {
        respond(200, """
                {
                  "totalArticles": 1,
                  "unknownTopLevel": true,
                  "articles": [{
                    "title": "  Grid battery project announced  ",
                    "url": "https://news.example.com/grid-battery",
                    "description": "  A utility-scale project.  ",
                    "content": "This provider content must not be mapped.",
                    "publishedAt": "2026-08-26T10:15:30Z",
                    "source": {
                      "name": "  Example Energy News  ",
                      "url": "https://news.example.com",
                      "unknownSourceField": "ignored"
                    },
                    "unknownArticleField": {"ignored": true}
                  }]
                }
                """);
        NewsDiscoveryQuery query = new NewsDiscoveryQuery(
                "battery energy & storage",
                Instant.parse("2026-08-26T12:30:00Z"),
                Instant.parse("2026-08-27T23:59:59Z"),
                7
        );

        List<DiscoveredArticle> result = provider(10).discover(query);

        assertThat(result).containsExactly(new DiscoveredArticle(
                "Grid battery project announced",
                "https://news.example.com/grid-battery",
                "A utility-scale project.",
                "Example Energy News",
                Instant.parse("2026-08-26T10:15:30Z")
        ));
        assertThat(requests).hasSize(1);
        RecordedRequest request = requests.getFirst();
        assertThat(request.rawQuery()).contains("q=battery%20energy%20%26%20storage");
        assertThat(request.parameters())
                .containsEntry("q", "battery energy & storage")
                .containsEntry("apikey", API_KEY)
                .containsEntry("max", "7")
                .containsEntry("page", "1")
                .containsEntry("sortby", "publishedAt")
                .containsEntry("from", "2026-08-26T12:30:00Z")
                .containsEntry("to", "2026-08-27T23:59:59Z")
                .doesNotContainKeys("country", "lang");
        assertThat(request.accept()).isEqualTo("application/json");
    }

    @Test
    void omitsTimeBoundsWhenTheyAreAbsent() {
        respond(200, "{\"totalArticles\":0,\"articles\":[]}");

        assertThat(provider(10).discover(query(7))).isEmpty();

        assertThat(requests).hasSize(1);
        assertThat(requests.getFirst().parameters())
                .containsEntry("max", "7")
                .containsEntry("page", "1")
                .containsEntry("sortby", "publishedAt")
                .doesNotContainKeys("from", "to");
    }

    @Test
    void mapsAnAvailableSingleTimeBoundWithoutInventingTheOther() {
        respond(200, "{\"totalArticles\":0,\"articles\":[]}");
        NewsDiscoveryQuery query = new NewsDiscoveryQuery(
                "BESS",
                Instant.parse("2026-08-26T00:00:00Z"),
                null,
                5
        );

        assertThat(provider(10).discover(query)).isEmpty();

        assertThat(requests.getFirst().parameters())
                .containsEntry("from", "2026-08-26T00:00:00Z")
                .doesNotContainKey("to");
    }

    @Test
    void paginatesUsingConfiguredPageSizeAndOneBasedPages() {
        respond(200, responseWithArticles(25, 0, 10));
        respond(200, responseWithArticles(25, 10, 10));
        respond(200, responseWithArticles(25, 20, 5));

        List<DiscoveredArticle> result = provider(10).discover(query(25));

        assertThat(result).hasSize(25);
        assertThat(requests).hasSize(3);
        assertThat(requests)
                .extracting(request -> request.parameters().get("page"))
                .containsExactly("1", "2", "3");
        assertThat(requests)
                .extracting(request -> request.parameters().get("max"))
                .containsExactly("10", "10", "5");
    }

    @Test
    void respectsConfiguredPageSizeAboveTheFreePlanDefault() {
        respond(200, responseWithArticles(20, 0, 20));

        assertThat(provider(25).discover(query(20))).hasSize(20);

        assertThat(requests).hasSize(1);
        assertThat(requests.getFirst().parameters()).containsEntry("max", "20");
    }

    @Test
    void stopsWhenGNewsReturnsAShortPage() {
        respond(200, responseWithArticles(100, 0, 2));

        assertThat(provider(10).discover(query(25))).hasSize(2);

        assertThat(requests).hasSize(1);
    }

    @Test
    void stopsWhenTotalArticlesHasBeenReached() {
        respond(200, responseWithArticles(10, 0, 10));

        assertThat(provider(10).discover(query(25))).hasSize(10);

        assertThat(requests).hasSize(1);
    }

    @Test
    void stopsWhenAFullPageContainsNoValidCandidates() {
        String invalidArticles = java.util.stream.IntStream.range(0, 10)
                .mapToObj(index -> "{\"title\":\"Candidate " + index + "\"}")
                .collect(java.util.stream.Collectors.joining(","));
        respond(200, "{\"totalArticles\":100,\"articles\":[" + invalidArticles + "]}");

        assertThat(provider(10).discover(query(25))).isEmpty();

        assertThat(requests).hasSize(1);
    }

    @Test
    void neverReturnsMoreThanNeutralQueryLimit() {
        respond(200, responseWithArticles(100, 0, 12));

        assertThat(provider(10).discover(query(7)))
                .hasSize(7)
                .extracting(DiscoveredArticle::title)
                .containsExactly(
                        "Candidate 0",
                        "Candidate 1",
                        "Candidate 2",
                        "Candidate 3",
                        "Candidate 4",
                        "Candidate 5",
                        "Candidate 6"
                );
        assertThat(requests).hasSize(1);
    }

    @Test
    void keepsOptionalFieldsNullableAndSkipsMissingRequiredCandidates() {
        respond(200, """
                {
                  "totalArticles": 4,
                  "articles": [
                    {
                      "title": "Candidate without metadata",
                      "url": "https://example.com/one"
                    },
                    {
                      "title": "Candidate with malformed date",
                      "url": "https://example.com/two",
                      "description": " ",
                      "publishedAt": "yesterday",
                      "source": {"name": " "}
                    },
                    {"title": " ", "url": "https://example.com/invalid"},
                    {"title": "Missing URL"},
                    null
                  ]
                }
                """);

        List<DiscoveredArticle> result = provider(10).discover(query(10));

        assertThat(result).hasSize(2);
        assertThat(result).allSatisfy(article -> {
            assertThat(article.description()).isNull();
            assertThat(article.sourceName()).isNull();
            assertThat(article.publishedAt()).isNull();
        });
    }

    @Test
    void rejectsQueryBeyondGNewsLimitWithoutCallingApi() {
        NewsDiscoveryQuery query = new NewsDiscoveryQuery("x".repeat(201), null, null, 10);

        assertThatThrownBy(() -> provider(10).discover(query))
                .isInstanceOf(NewsDiscoveryException.class)
                .hasMessage("gnews keyword exceeds GNews's 200 character limit");
        assertThat(requests).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({
            "400,rejected the request",
            "401,authentication failed",
            "403,quota was exceeded",
            "429,rate limited",
            "500,unavailable"
    })
    void mapsHttpFailuresWithoutExposingKeyUriOrBody(int status, String message) {
        respond(status, "{\"errors\":[\"sensitive provider response\"]}");

        assertThatThrownBy(() -> provider(10).discover(query(10)))
                .isInstanceOf(NewsDiscoveryException.class)
                .hasMessageContaining("gnews")
                .hasMessageContaining("HTTP " + status)
                .hasMessageContaining(message)
                .hasMessageContaining("battery energy storage")
                .hasMessageNotContaining(API_KEY)
                .hasMessageNotContaining("apikey")
                .hasMessageNotContaining("http://")
                .hasMessageNotContaining("sensitive provider response");
        assertThat(requests).hasSize(1);
    }

    @Test
    void mapsMalformedJsonWithoutExposingResponse() {
        respond(200, "malformed-sensitive-body");

        assertThatThrownBy(() -> provider(10).discover(query(10)))
                .isInstanceOf(NewsDiscoveryException.class)
                .hasMessage("gnews returned invalid JSON for keyword 'battery energy storage'")
                .hasMessageNotContaining("malformed-sensitive-body")
                .hasMessageNotContaining(API_KEY);
    }

    @Test
    void mapsRequestTimeoutWithoutRetryingOrExposingUri() {
        responses.add(new TestResponse(200, "{\"articles\":[]}", Duration.ofMillis(500)));

        assertThatThrownBy(() -> provider(10, Duration.ofMillis(50)).discover(query(10)))
                .isInstanceOf(NewsDiscoveryException.class)
                .hasMessage("gnews discovery timed out for keyword 'battery energy storage'")
                .hasMessageNotContaining(API_KEY)
                .hasMessageNotContaining("http://");
        assertThat(requests).hasSize(1);
    }

    @Test
    void mapsNetworkFailureWithoutExposingKeyOrUri() throws IOException {
        int unavailablePort;
        try (ServerSocket socket = new ServerSocket(0)) {
            unavailablePort = socket.getLocalPort();
        }
        GNewsNewsDiscoveryProvider provider = new GNewsNewsDiscoveryProvider(
                API_KEY,
                10,
                new ObjectMapper(),
                URI.create("http://127.0.0.1:" + unavailablePort + "/api/v4/search"),
                HttpClient.newBuilder().connectTimeout(Duration.ofMillis(200)).build(),
                Duration.ofSeconds(1)
        );

        assertThatThrownBy(() -> provider.discover(query(10)))
                .isInstanceOf(NewsDiscoveryException.class)
                .hasMessage("gnews discovery request failed for keyword 'battery energy storage'")
                .hasMessageNotContaining(API_KEY)
                .hasMessageNotContaining("http://");
    }

    private GNewsNewsDiscoveryProvider provider(int maxResultsPerRequest) {
        return provider(maxResultsPerRequest, Duration.ofSeconds(2));
    }

    private GNewsNewsDiscoveryProvider provider(
            int maxResultsPerRequest,
            Duration requestTimeout
    ) {
        return new GNewsNewsDiscoveryProvider(
                API_KEY,
                maxResultsPerRequest,
                new ObjectMapper(),
                URI.create("http://127.0.0.1:" + server.getAddress().getPort()
                        + "/api/v4/search"),
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build(),
                requestTimeout
        );
    }

    private NewsDiscoveryQuery query(int limit) {
        return new NewsDiscoveryQuery("battery energy storage", null, null, limit);
    }

    private void respond(int status, String body) {
        responses.add(new TestResponse(status, body, Duration.ZERO));
    }

    private void handle(HttpExchange exchange) throws IOException {
        synchronized (requests) {
            requests.add(new RecordedRequest(
                    exchange.getRequestURI().getRawQuery(),
                    queryParameters(exchange.getRequestURI().getRawQuery()),
                    exchange.getRequestHeaders().getFirst("Accept")
            ));
        }
        TestResponse response = responses.remove();
        if (!response.delay().isZero()) {
            try {
                Thread.sleep(response.delay());
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return;
            }
        }

        byte[] body = response.body().getBytes(StandardCharsets.UTF_8);
        try {
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(response.status(), body.length);
            exchange.getResponseBody().write(body);
        } finally {
            exchange.close();
        }
    }

    private Map<String, String> queryParameters(String rawQuery) {
        Map<String, String> parameters = new LinkedHashMap<>();
        Arrays.stream(rawQuery.split("&"))
                .map(parameter -> parameter.split("=", 2))
                .forEach(parts -> parameters.put(
                        decode(parts[0]),
                        parts.length == 1 ? "" : decode(parts[1])
                ));
        return parameters;
    }

    private String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private String responseWithArticles(long totalArticles, int start, int count) {
        String articles = java.util.stream.IntStream.range(start, start + count)
                .mapToObj(index -> """
                        {
                          "title": "Candidate %d",
                          "url": "https://example.com/candidate-%d"
                        }
                        """.formatted(index, index))
                .collect(java.util.stream.Collectors.joining(","));
        return "{\"totalArticles\":" + totalArticles + ",\"articles\":[" + articles + "]}";
    }

    private record TestResponse(int status, String body, Duration delay) {
    }

    private record RecordedRequest(
            String rawQuery,
            Map<String, String> parameters,
            String accept
    ) {
    }
}
