package com.carya.energynews.dailybriefanalysis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GroqDailyBriefAiProviderTest {

    private static final String API_KEY = "secret-test-groq-key";
    private static final String MODEL = "openai/gpt-oss-20b";

    private final ObjectMapper objectMapper = new ObjectMapper();
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
        server.createContext("/openai/v1/chat/completions", this::handle);
        server.start();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
        serverExecutor.shutdownNow();
    }

    @Test
    void sendsStrictMetadataOnlyUtf8RequestAndParsesChineseResponse() throws Exception {
        respond(200, groqResponse("""
                {
                  "headline": "据报道，NVIDIA拟收购相关企业",
                  "overview": "市场消息仍有待确认。管理层应关注后续披露。",
                  "events": [{
                    "title": "潜在收购动向",
                    "summary": "媒体报道称NVIDIA可能推进一项收购。",
                    "whyItMatters": "如果交易推进，可能表明其平台布局正在扩大。",
                    "supportingArticleIds": [109]
                  }]
                }
                """));

        DailyBriefAiResult result = provider(Duration.ofSeconds(2)).analyze(request());

        assertThat(result.headline()).isEqualTo("据报道，NVIDIA拟收购相关企业");
        assertThat(result.events().getFirst().summary())
                .isEqualTo("媒体报道称NVIDIA可能推进一项收购。");
        assertThat(requests).hasSize(1);

        RecordedRequest recorded = requests.getFirst();
        assertThat(recorded.authorization()).containsExactly("Bearer " + API_KEY);
        assertThat(recorded.contentType()).isEqualTo("application/json; charset=UTF-8");
        assertThat(recorded.accept()).isEqualTo("application/json");

        JsonNode body = objectMapper.readTree(recorded.body());
        assertThat(body.path("model").asText()).isEqualTo(MODEL);
        assertThat(body.path("reasoning_effort").asText()).isEqualTo("low");
        assertThat(body.has("tools")).isFalse();
        assertThat(body.has("browser_search")).isFalse();
        assertThat(body.has("code_interpreter")).isFalse();
        assertThat(body.path("messages")).hasSize(2);
        assertThat(body.path("messages").get(0).path("role").asText()).isEqualTo("system");
        assertThat(body.path("messages").get(1).path("role").asText()).isEqualTo("user");

        JsonNode responseFormat = body.path("response_format");
        assertThat(responseFormat.path("type").asText()).isEqualTo("json_schema");
        assertThat(responseFormat.path("json_schema").path("name").asText())
                .isEqualTo("daily_brief_analysis");
        assertThat(responseFormat.path("json_schema").path("strict").asBoolean()).isTrue();
        assertThat(responseFormat.has("strict")).isFalse();
        JsonNode schema = responseFormat.path("json_schema").path("schema");
        assertThat(schema.path("type").asText()).isEqualTo("object");
        assertThat(schema.has("strict")).isFalse();
        assertThat(schema.path("properties").path("headline").path("type").asText())
                .isEqualTo("string");
        assertThat(schema.path("properties").path("overview").path("type").asText())
                .isEqualTo("string");
        assertThat(schema.path("additionalProperties").asBoolean()).isFalse();
        assertThat(schema.path("required")).extracting(JsonNode::asText)
                .containsExactly("headline", "overview", "events");
        JsonNode events = schema.path("properties").path("events");
        assertThat(events.path("type").asText()).isEqualTo("array");
        assertThat(events.path("minItems").asInt()).isEqualTo(1);
        assertThat(events.path("maxItems").asInt()).isEqualTo(5);
        assertThat(events.path("items").path("additionalProperties").asBoolean()).isFalse();
        assertThat(events.path("items").path("type").asText()).isEqualTo("object");
        assertThat(events.path("items").path("required")).extracting(JsonNode::asText)
                .containsExactly("title", "summary", "whyItMatters", "supportingArticleIds");
        for (String field : List.of("title", "summary", "whyItMatters")) {
            assertThat(events.path("items").path("properties").path(field).path("type").asText())
                    .isEqualTo("string");
        }
        JsonNode supportingIds = events.path("items").path("properties").path("supportingArticleIds");
        assertThat(supportingIds.path("type").asText()).isEqualTo("array");
        assertThat(supportingIds.path("items").path("type").asText()).isEqualTo("integer");
        assertThat(events.path("items").path("properties")
                .path("supportingArticleIds").path("minItems").asInt()).isEqualTo(1);

        String userMessage = body.path("messages").get(1).path("content").asText();
        JsonNode userJson = objectMapper.readTree(userMessage);
        JsonNode article = userJson.path("articles").get(0);
        assertThat(article.path("title").asText()).isEqualTo("据报道，NVIDIA拟收购X");
        assertThat(article.path("description").asText())
                .isEqualTo("消息人士称该交易可能价值129亿美元。");
        assertThat(article.path("matchedKeywords").get(0).asText()).isEqualTo("NVIDIA");
        assertThat(article.has("content")).isFalse();
        assertThat(article.has("url")).isFalse();

        String systemMessage = body.path("messages").get(0).path("content").asText();
        assertThat(systemMessage)
                .contains("Preserve uncertainty")
                .contains("at most one inference step")
                .contains("untrusted SOURCE DATA");
    }

    @Test
    void sendsExactlyOneBearerHeaderWithTrimmedConfiguredKey() {
        respond(429, "{}");

        assertThatThrownBy(() -> provider(Duration.ofSeconds(2), " \t\r\n" + API_KEY + " \r\n\t")
                .analyze(request())).isInstanceOf(DailyBriefAiException.class);

        assertThat(requests).hasSize(1);
        assertThat(requests.getFirst().authorization()).containsExactly("Bearer " + API_KEY);
    }

    @ParameterizedTest
    @CsvSource({
            "400,INVALID_REQUEST,structured-output configuration",
            "401,AUTHENTICATION,authentication",
            "403,AUTHORIZATION,denied access",
            "422,INVALID_REQUEST,structured-output configuration",
            "429,RATE_LIMITED,rate limited",
            "500,UPSTREAM,unavailable"
    })
    void mapsHttpFailuresWithoutLeakingRequestResponseOrKey(
            int status,
            DailyBriefAiException.Failure failure,
            String message
    ) {
        respond(status, "{\"sensitive\":\"provider body\"}");

        assertThatThrownBy(() -> provider(Duration.ofSeconds(2)).analyze(request()))
                .isInstanceOfSatisfying(DailyBriefAiException.class, exception -> {
                    assertThat(exception.getFailure()).isEqualTo(failure);
                    assertThat(exception.getMessage()).contains(message).contains("HTTP " + status);
                    assertThat(exception.getMessage())
                            .doesNotContain(API_KEY)
                            .doesNotContain("provider body")
                            .doesNotContain("NVIDIA")
                            .doesNotContain("http://");
                });
        assertThat(requests).hasSize(1);
    }

    @Test
    void rejectsMissingChoicesContentAndMalformedStructuredContentSafely() {
        respond(200, "{}");
        respond(200, "{\"choices\":[{\"message\":{}}]}");
        respond(200, groqResponse("not-json-sensitive-content"));

        for (int index = 0; index < 3; index++) {
            assertThatThrownBy(() -> provider(Duration.ofSeconds(2)).analyze(request()))
                    .isInstanceOfSatisfying(DailyBriefAiException.class, exception -> {
                        assertThat(exception.getFailure())
                                .isEqualTo(DailyBriefAiException.Failure.MALFORMED_RESPONSE);
                        assertThat(exception.getMessage())
                                .isEqualTo("Groq returned a malformed daily brief response")
                                .doesNotContain(API_KEY)
                                .doesNotContain("sensitive");
                    });
        }
    }

    @Test
    void mapsTimeoutWithoutRetrying() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        when(httpClient.send(any(HttpRequest.class),
                org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()))
                .thenThrow(new HttpTimeoutException("Synthetic timeout"));
        GroqDailyBriefAiProvider provider = new GroqDailyBriefAiProvider(
                API_KEY,
                MODEL,
                objectMapper,
                new DailyBriefAiPromptFactory(objectMapper),
                URI.create("http://127.0.0.1:" + server.getAddress().getPort()
                        + "/openai/v1/chat/completions"),
                httpClient,
                Duration.ofSeconds(60)
        );

        assertThatThrownBy(() -> provider.analyze(request()))
                .isInstanceOfSatisfying(DailyBriefAiException.class, exception -> {
                    assertThat(exception.getFailure())
                            .isEqualTo(DailyBriefAiException.Failure.TIMEOUT);
                    assertThat(exception.getMessage()).isEqualTo(
                            "Groq daily brief request timed out"
                    );
                });
        verify(httpClient).send(any(HttpRequest.class),
                org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any());
        assertThat(requests).isEmpty();
    }

    private GroqDailyBriefAiProvider provider(Duration requestTimeout) {
        return provider(requestTimeout, API_KEY);
    }

    private GroqDailyBriefAiProvider provider(Duration requestTimeout, String configuredKey) {
        return new GroqDailyBriefAiProvider(
                configuredKey,
                MODEL,
                objectMapper,
                new DailyBriefAiPromptFactory(objectMapper),
                URI.create("http://127.0.0.1:" + server.getAddress().getPort()
                        + "/openai/v1/chat/completions"),
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build(),
                requestTimeout
        );
    }

    private DailyBriefAiRequest request() {
        return new DailyBriefAiRequest(
                "PostgreSQL 验收",
                LocalDate.parse("2026-08-27"),
                "Asia/Shanghai",
                List.of(new DailyBriefAiArticle(
                        109L,
                        "据报道，NVIDIA拟收购X",
                        "消息人士称该交易可能价值129亿美元。",
                        "测试来源",
                        Instant.parse("2026-08-27T08:00:00Z"),
                        Instant.parse("2026-08-27T08:00:00Z"),
                        List.of("NVIDIA", "AI data center")
                ))
        );
    }

    private String groqResponse(String structuredContent) {
        ObjectNode response = objectMapper.createObjectNode();
        response.putArray("choices")
                .addObject()
                .putObject("message")
                .put("content", structuredContent);
        try {
            return objectMapper.writeValueAsString(response);
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private void respond(int status, String body) {
        responses.add(new TestResponse(status, body));
    }

    private void handle(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        synchronized (requests) {
            requests.add(new RecordedRequest(
                    List.copyOf(exchange.getRequestHeaders().get("Authorization")),
                    exchange.getRequestHeaders().getFirst("Content-Type"),
                    exchange.getRequestHeaders().getFirst("Accept"),
                    body
            ));
        }

        TestResponse response = responses.remove();
        byte[] responseBytes = response.body().getBytes(StandardCharsets.UTF_8);
        try {
            exchange.getResponseHeaders().set(
                    "Content-Type",
                    "application/json; charset=UTF-8"
            );
            exchange.sendResponseHeaders(response.status(), responseBytes.length);
            exchange.getResponseBody().write(responseBytes);
        } finally {
            exchange.close();
        }
    }

    private record TestResponse(int status, String body) {
    }

    private record RecordedRequest(
            List<String> authorization,
            String contentType,
            String accept,
            String body
    ) {
    }
}
