package com.carya.energynews.translation;

import com.carya.energynews.source.SourceLanguage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DeepLTranslationProviderTest {

    private static final String API_KEY = "test-api-key";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AtomicInteger requestCount = new AtomicInteger();
    private final List<String> requestBodies = new CopyOnWriteArrayList<>();
    private final AtomicReference<String> authorizationHeader = new AtomicReference<>();

    private HttpServer server;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void translatesTitleAndPreservesNullDescriptionWithMappedLanguages() throws Exception {
        serve(200, """
                {
                  "translations": [
                    {"detected_source_language": "EN", "text": "家庭电池安装量增长"}
                  ]
                }
                """);

        TranslationOutput output = provider(API_KEY).translate(input(null));

        assertThat(output).isEqualTo(new TranslationOutput("家庭电池安装量增长", null));
        assertThat(output.translatedContent()).isNull();
        assertThat(requestCount).hasValue(1);
        assertThat(authorizationHeader).hasValue("DeepL-Auth-Key " + API_KEY);

        JsonNode request = requestJson();
        assertThat(request.path("source_lang").asText()).isEqualTo("EN");
        assertThat(request.path("target_lang").asText()).isEqualTo("ZH-HANS");
        assertThat(request.path("text").size()).isEqualTo(1);
        assertThat(request.path("text").get(0).asText()).isEqualTo("Home battery installations climb");
    }

    @Test
    void translatesTitleAndDescriptionInOneRequestAndPreservesOrdering() throws Exception {
        serve(200, """
                {
                  "translations": [
                    {"text": "家庭电池安装量增长"},
                    {"text": "电池储能部署继续增长。"}
                  ]
                }
                """);

        TranslationOutput output = provider(API_KEY).translate(input(
                "Battery storage deployments continue to grow."
        ));

        assertThat(output).isEqualTo(new TranslationOutput(
                "家庭电池安装量增长",
                "电池储能部署继续增长。"
        ));
        assertThat(requestCount).hasValue(1);

        JsonNode request = requestJson();
        assertThat(request.path("text").size()).isEqualTo(2);
        assertThat(request.path("text").get(0).asText()).isEqualTo("Home battery installations climb");
        assertThat(request.path("text").get(1).asText())
                .isEqualTo("Battery storage deployments continue to grow.");
    }

    @Test
    void translatesShortTitleDescriptionAndContentWithParagraphMapping() throws Exception {
        serveSequence(
                response(200, """
                        {
                          "translations": [
                            {"text": "密歇根州电池工厂开业"},
                            {"text": "该工厂扩大了美国电池产能。"}
                          ]
                        }
                        """),
                response(200, """
                        {
                          "translations": [
                            {"text": "第一段介绍了新的电池工厂。"},
                            {"text": "第二段说明了该项目的产能。"}
                          ]
                        }
                        """)
        );

        TranslationOutput output = provider(API_KEY).translate(input(
                "The factory expands United States battery capacity.",
                "The first paragraph introduces the new battery factory.\n\n"
                        + "The second paragraph explains the project's capacity."
        ));

        assertThat(output).isEqualTo(new TranslationOutput(
                "密歇根州电池工厂开业",
                "该工厂扩大了美国电池产能。",
                "第一段介绍了新的电池工厂。\n\n第二段说明了该项目的产能。"
        ));
        assertThat(requestCount).hasValue(2);
        assertThat(requestJson(0).path("text").size()).isEqualTo(2);
        assertThat(requestJson(1).path("text").size()).isEqualTo(2);
    }

    @Test
    void dividesLongContentAndReconstructsParagraphsInOrder() {
        serveEchoingTranslations();
        String first = "A".repeat(30_000);
        String second = "B".repeat(30_000);
        String third = "C".repeat(30_000);
        String content = first + "\n\n" + second + "\n\n" + third;

        TranslationOutput output = provider(API_KEY).translate(contentOnlyInput(content));

        assertThat(output).isEqualTo(new TranslationOutput(null, null, content));
        assertThat(requestCount.get()).isGreaterThan(1);
        assertThat(requestBodies).allSatisfy(body ->
                assertThat(body.getBytes(StandardCharsets.UTF_8).length).isLessThanOrEqualTo(64 * 1024)
        );
    }

    @Test
    void splitsUtf8ContentWithoutBreakingUnicodeCharacters() throws Exception {
        serveEchoingTranslations();
        String content = "储".repeat(30_000);

        TranslationOutput output = provider(API_KEY).translate(contentOnlyInput(content));

        assertThat(output.translatedContent()).isEqualTo(content);
        assertThat(requestCount.get()).isGreaterThan(1);
        for (int index = 0; index < requestBodies.size(); index++) {
            JsonNode texts = requestJson(index).path("text");
            assertThat(texts).allSatisfy(text ->
                    assertThat(text.asText()).doesNotContain("�")
            );
        }
    }

    @Test
    void failsTheWholeOperationWhenALaterContentChunkFails() {
        serve((index, body) -> index == 0
                ? echoResponse(body)
                : response(503, "Temporarily unavailable"));
        String content = "A".repeat(30_000)
                + "\n\n" + "B".repeat(30_000)
                + "\n\n" + "C".repeat(30_000);

        assertThatThrownBy(() -> provider(API_KEY).translate(contentOnlyInput(content)))
                .isInstanceOf(TranslationException.class)
                .hasMessage("DeepL translation request failed with HTTP 503");

        assertThat(requestCount).hasValue(2);
    }

    @Test
    void reportsMalformedLaterContentChunkResponse() {
        serve((index, body) -> index == 0
                ? echoResponse(body)
                : response(200, "{not-json"));
        String content = "A".repeat(30_000)
                + "\n\n" + "B".repeat(30_000)
                + "\n\n" + "C".repeat(30_000);

        assertThatThrownBy(() -> provider(API_KEY).translate(contentOnlyInput(content)))
                .isInstanceOf(TranslationException.class)
                .hasMessage("Unable to parse DeepL translation response");

        assertThat(requestCount).hasValue(2);
    }

    @Test
    void reportsNonSuccessfulResponses() {
        serve(429, "Rate limit exceeded");

        assertThatThrownBy(() -> provider(API_KEY).translate(input(null)))
                .isInstanceOf(TranslationException.class)
                .hasMessage("DeepL translation request failed with HTTP 429");
    }

    @Test
    void reportsMalformedJsonResponses() {
        serve(200, "{not-json");

        assertThatThrownBy(() -> provider(API_KEY).translate(input(null)))
                .isInstanceOf(TranslationException.class)
                .hasMessage("Unable to parse DeepL translation response");
    }

    @Test
    void reportsUnexpectedTranslationCount() {
        serve(200, """
                {
                  "translations": [
                    {"text": "家庭电池安装量增长"}
                  ]
                }
                """);

        assertThatThrownBy(() -> provider(API_KEY).translate(input("Description")))
                .isInstanceOf(TranslationException.class)
                .hasMessage("DeepL returned 1 translations, but 2 were expected");
    }

    @Test
    void reportsMissingApiKeyWithoutSendingARequest() {
        assertThatThrownBy(() -> provider("").translate(input(null)))
                .isInstanceOf(TranslationException.class)
                .hasMessage("DeepL API key is not configured");

        assertThat(requestCount).hasValue(0);
    }

    @Test
    void reportsUnreachableServer() {
        DeepLTranslationProvider provider = provider(API_KEY);
        server.stop(0);
        server = null;

        assertThatThrownBy(() -> provider.translate(input(null)))
                .isInstanceOf(TranslationException.class)
                .hasMessage("Unable to call DeepL translation API");
    }

    @Test
    void doesNotExposeApiKeyInHttpErrorMessage() {
        String secretApiKey = "secret-key-that-must-not-leak";
        serve(403, "Invalid key: " + secretApiKey);

        assertThatThrownBy(() -> provider(secretApiKey).translate(input(null)))
                .isInstanceOf(TranslationException.class)
                .hasMessage("DeepL translation request failed with HTTP 403")
                .hasMessageNotContaining(secretApiKey);
    }

    @Test
    void doesNotExposeMalformedApiKeyInRequestCreationError() {
        String secretApiKey = "secret-key\nthat-must-not-leak";

        assertThatThrownBy(() -> provider(secretApiKey).translate(input(null)))
                .isInstanceOf(TranslationException.class)
                .hasMessage("Unable to create DeepL translation request")
                .hasMessageNotContaining(secretApiKey)
                .hasNoCause();
    }

    @Test
    void rejectsUnsupportedLanguageCombination() {
        TranslationInput unsupported = new TranslationInput(
                SourceLanguage.ZH_CN,
                TranslationLanguage.ZH_CN,
                "标题",
                null
        );

        assertThatThrownBy(() -> provider(API_KEY).translate(unsupported))
                .isInstanceOf(TranslationException.class)
                .hasMessage("DeepL supports only EN to ZH_CN translation in V1");
    }

    private TranslationInput input(String description) {
        return input(description, null);
    }

    private TranslationInput input(String description, String content) {
        return new TranslationInput(
                SourceLanguage.EN,
                TranslationLanguage.ZH_CN,
                "Home battery installations climb",
                description,
                content
        );
    }

    private TranslationInput contentOnlyInput(String content) {
        return new TranslationInput(
                SourceLanguage.EN,
                TranslationLanguage.ZH_CN,
                null,
                null,
                content
        );
    }

    private DeepLTranslationProvider provider(String apiKey) {
        return new DeepLTranslationProvider(
                new DeepLTranslationProperties(baseUrl(), apiKey),
                objectMapper
        );
    }

    private String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private JsonNode requestJson() throws IOException {
        return requestJson(requestBodies.size() - 1);
    }

    private JsonNode requestJson(int index) throws IOException {
        return objectMapper.readTree(requestBodies.get(index));
    }

    private void serve(int status, String response) {
        serve((index, request) -> response(status, response));
    }

    private void serveSequence(TestResponse... responses) {
        serve((index, request) -> {
            if (index >= responses.length) {
                throw new IOException("No configured response for request " + index);
            }
            return responses[index];
        });
    }

    private void serveEchoingTranslations() {
        serve((index, request) -> echoResponse(request));
    }

    private TestResponse echoResponse(String request) throws IOException {
        JsonNode requestJson = objectMapper.readTree(request);
        ObjectNode response = objectMapper.createObjectNode();
        ArrayNode translations = response.putArray("translations");
        for (JsonNode text : requestJson.path("text")) {
            translations.addObject().put("text", text.asText());
        }
        return response(200, objectMapper.writeValueAsString(response));
    }

    private TestResponse response(int status, String body) {
        return new TestResponse(status, body);
    }

    private void serve(ResponseFactory responseFactory) {
        server.createContext("/v2/translate", exchange -> {
            int requestIndex = requestCount.getAndIncrement();
            authorizationHeader.set(exchange.getRequestHeaders().getFirst("Authorization"));
            String request = new String(
                    exchange.getRequestBody().readAllBytes(),
                    StandardCharsets.UTF_8
            );
            requestBodies.add(request);
            TestResponse configuredResponse = responseFactory.create(requestIndex, request);

            byte[] body = configuredResponse.body().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            exchange.sendResponseHeaders(configuredResponse.status(), body.length);
            try (var output = exchange.getResponseBody()) {
                output.write(body);
            }
        });
    }

    @FunctionalInterface
    private interface ResponseFactory {

        TestResponse create(int requestIndex, String requestBody) throws IOException;
    }

    private record TestResponse(int status, String body) {
    }
}
