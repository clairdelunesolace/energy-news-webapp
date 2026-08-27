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

class BraveNewsDiscoveryProviderTest {

    private static final String API_KEY = "test-api-key";

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
        server.createContext("/res/v1/news/search", this::handle);
        server.start();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
        serverExecutor.shutdownNow();
    }

    @Test
    void mapsEncodedRequestAndDocumentedResponseFields() {
        respond(200, """
                {
                  "type": "news",
                  "unknown_top_level": true,
                  "results": [{
                    "title": "  Grid battery project announced  ",
                    "url": "https://news.example.com/grid-battery",
                    "description": "  A utility-scale project.  ",
                    "page_age": "2026-08-26T10:15:30Z",
                    "age": "3 hours ago",
                    "profile": {
                      "name": "Example",
                      "long_name": "Example Energy News",
                      "unknown_profile_field": "ignored"
                    },
                    "meta_url": {"hostname": "news.example.com"},
                    "unknown_result_field": {"ignored": true}
                  }]
                }
                """);
        NewsDiscoveryQuery query = new NewsDiscoveryQuery(
                "battery energy & storage",
                Instant.parse("2026-08-26T12:30:00Z"),
                Instant.parse("2026-08-27T23:59:59Z"),
                20
        );

        List<DiscoveredArticle> result = provider().discover(query);

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
        assertThat(request.parameters()).containsEntry("q", "battery energy & storage")
                .containsEntry("country", "ALL")
                .containsEntry("count", "20")
                .containsEntry("offset", "0")
                .containsEntry("freshness", "2026-08-26to2026-08-27");
        assertThat(request.accept()).isEqualTo("application/json");
        assertThat(request.subscriptionToken()).isEqualTo(API_KEY);
    }

    @ParameterizedTest
    @CsvSource({"20,20", "50,50"})
    void limitsUpToFiftyUseOneRequest(int limit, int expectedCount) {
        respond(200, "{\"results\":[]}");

        assertThat(provider().discover(query(limit))).isEmpty();

        assertThat(requests).hasSize(1);
        assertThat(requests.getFirst().parameters())
                .containsEntry("count", Integer.toString(expectedCount))
                .containsEntry("offset", "0")
                .doesNotContainKey("freshness");
    }

    @Test
    void paginatesOnlyTheRequiredSecondPage() {
        respond(200, responseWithResults(0, 50));
        respond(200, responseWithResults(50, 30));

        List<DiscoveredArticle> result = provider().discover(query(80));

        assertThat(result).hasSize(80);
        assertThat(requests).hasSize(2);
        assertThat(requests.get(0).parameters())
                .containsEntry("count", "50")
                .containsEntry("offset", "0");
        assertThat(requests.get(1).parameters())
                .containsEntry("count", "30")
                .containsEntry("offset", "1");
    }

    @Test
    void limitOneHundredUsesTwoFiftyResultPages() {
        respond(200, responseWithResults(0, 50));
        respond(200, responseWithResults(50, 50));

        assertThat(provider().discover(query(100))).hasSize(100);

        assertThat(requests).hasSize(2);
        assertThat(requests)
                .extracting(request -> request.parameters().get("count"))
                .containsExactly("50", "50");
        assertThat(requests)
                .extracting(request -> request.parameters().get("offset"))
                .containsExactly("0", "1");
    }

    @Test
    void stopsWhenBraveReturnsAShortPage() {
        respond(200, responseWithResults(0, 2));

        assertThat(provider().discover(query(80))).hasSize(2);

        assertThat(requests).hasSize(1);
    }

    @Test
    void stopsWhenAFullPageContainsNoValidCandidates() {
        String invalidCandidates = java.util.stream.IntStream.range(0, 50)
                .mapToObj(index -> "{\"title\":\"Candidate " + index + "\"}")
                .collect(java.util.stream.Collectors.joining(","));
        respond(200, "{\"results\":[" + invalidCandidates + "]}");

        assertThat(provider().discover(query(80))).isEmpty();

        assertThat(requests).hasSize(1);
    }

    @Test
    void rejectsOneSidedDateRangeWithoutCallingBrave() {
        NewsDiscoveryQuery query = new NewsDiscoveryQuery(
                "BESS",
                Instant.parse("2026-08-26T00:00:00Z"),
                null,
                20
        );

        assertThatThrownBy(() -> provider().discover(query))
                .isInstanceOf(NewsDiscoveryException.class)
                .hasMessage("brave-news requires both from and to dates for discovery freshness");
        assertThat(requests).isEmpty();
    }

    @Test
    void keepsOptionalFieldsNullableAndSkipsMissingRequiredCandidates() {
        respond(200, """
                {
                  "results": [
                    {
                      "title": "Candidate without metadata",
                      "url": "https://fallback.example/path"
                    },
                    {
                      "title": "Candidate with bad date",
                      "url": "https://other.example/path",
                      "description": " ",
                      "page_age": "2026-08-26T10:15:30",
                      "age": "3 hours ago",
                      "meta_url": {"hostname": "other.example"}
                    },
                    {"title": " ", "url": "https://invalid.example"},
                    {"title": "Missing URL"},
                    null
                  ]
                }
                """);

        List<DiscoveredArticle> result = provider().discover(query(20));

        assertThat(result).hasSize(2);
        assertThat(result.get(0).description()).isNull();
        assertThat(result.get(0).sourceName()).isEqualTo("fallback.example");
        assertThat(result.get(0).publishedAt()).isNull();
        assertThat(result.get(1).description()).isNull();
        assertThat(result.get(1).sourceName()).isEqualTo("other.example");
        assertThat(result.get(1).publishedAt()).isNull();
    }

    @ParameterizedTest
    @CsvSource({
            "401,authentication failed",
            "403,authentication failed",
            "429,rate limited",
            "500,unavailable"
    })
    void mapsProviderHttpFailuresWithoutExposingTheApiKey(int status, String message) {
        respond(status, "{\"sensitive\":\"response body is not included\"}");

        assertThatThrownBy(() -> provider().discover(query(20)))
                .isInstanceOf(NewsDiscoveryException.class)
                .hasMessageContaining("brave-news")
                .hasMessageContaining("HTTP " + status)
                .hasMessageContaining(message)
                .hasMessageContaining("battery energy storage")
                .hasMessageNotContaining(API_KEY)
                .hasMessageNotContaining("response body");
        assertThat(requests).hasSize(1);
    }

    @Test
    void mapsMalformedJson() {
        respond(200, "not-json");

        assertThatThrownBy(() -> provider().discover(query(20)))
                .isInstanceOf(NewsDiscoveryException.class)
                .hasMessage("brave-news returned invalid JSON for keyword 'battery energy storage'");
    }

    @Test
    void mapsRequestTimeoutWithoutRetrying() {
        responses.add(new TestResponse(200, "{\"results\":[]}", Duration.ofMillis(500)));

        assertThatThrownBy(() -> provider(Duration.ofMillis(50)).discover(query(20)))
                .isInstanceOf(NewsDiscoveryException.class)
                .hasMessage("brave-news discovery timed out for keyword 'battery energy storage'");
        assertThat(requests).hasSize(1);
    }

    @Test
    void rejectsKeywordBeyondBraveLimitWithoutCallingApi() {
        NewsDiscoveryQuery query = new NewsDiscoveryQuery("word ".repeat(51), null, null, 20);

        assertThatThrownBy(() -> provider().discover(query))
                .isInstanceOf(NewsDiscoveryException.class)
                .hasMessageContaining("50 word limit");
        assertThat(requests).isEmpty();
    }

    @Test
    void mapsInvalidTokenHeaderWithoutExposingToken() {
        String invalidApiKey = "invalid\nsecret";
        BraveNewsDiscoveryProvider provider = new BraveNewsDiscoveryProvider(
                invalidApiKey,
                new ObjectMapper(),
                URI.create("http://127.0.0.1:" + server.getAddress().getPort()
                        + "/res/v1/news/search"),
                HttpClient.newHttpClient(),
                Duration.ofSeconds(1)
        );

        assertThatThrownBy(() -> provider.discover(query(20)))
                .isInstanceOf(NewsDiscoveryException.class)
                .hasMessage("brave-news could not create a request for keyword 'battery energy storage'")
                .hasMessageNotContaining(invalidApiKey);
        assertThat(requests).isEmpty();
    }

    private BraveNewsDiscoveryProvider provider() {
        return provider(Duration.ofSeconds(2));
    }

    private BraveNewsDiscoveryProvider provider(Duration requestTimeout) {
        return new BraveNewsDiscoveryProvider(
                API_KEY,
                new ObjectMapper(),
                URI.create("http://127.0.0.1:" + server.getAddress().getPort()
                        + "/res/v1/news/search"),
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
                    exchange.getRequestHeaders().getFirst("Accept"),
                    exchange.getRequestHeaders().getFirst("X-Subscription-Token")
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

    private String responseWithResults(int start, int count) {
        String results = java.util.stream.IntStream.range(start, start + count)
                .mapToObj(index -> """
                        {
                          "title": "Candidate %d",
                          "url": "https://example.com/candidate-%d"
                        }
                        """.formatted(index, index))
                .collect(java.util.stream.Collectors.joining(","));
        return "{\"results\":[" + results + "]}";
    }

    private record TestResponse(int status, String body, Duration delay) {
    }

    private record RecordedRequest(
            String rawQuery,
            Map<String, String> parameters,
            String accept,
            String subscriptionToken
    ) {
    }
}
