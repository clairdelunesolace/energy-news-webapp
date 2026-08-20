package com.carya.energynews.translation;

import com.carya.energynews.source.SourceLanguage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DeepLTranslationProviderTest {

    private static final String API_KEY = "test-api-key";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AtomicInteger requestCount = new AtomicInteger();
    private final AtomicReference<String> requestBody = new AtomicReference<>();
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
        return new TranslationInput(
                SourceLanguage.EN,
                TranslationLanguage.ZH_CN,
                "Home battery installations climb",
                description
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
        return objectMapper.readTree(requestBody.get());
    }

    private void serve(int status, String response) {
        server.createContext("/v2/translate", exchange -> {
            requestCount.incrementAndGet();
            authorizationHeader.set(exchange.getRequestHeaders().getFirst("Authorization"));
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));

            byte[] body = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            exchange.sendResponseHeaders(status, body.length);
            try (var output = exchange.getResponseBody()) {
                output.write(body);
            }
        });
    }
}
