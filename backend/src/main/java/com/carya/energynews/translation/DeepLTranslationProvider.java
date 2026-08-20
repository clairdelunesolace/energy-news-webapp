package com.carya.energynews.translation;

import com.carya.energynews.source.SourceLanguage;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

@Component
public class DeepLTranslationProvider implements TranslationProvider {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    private final DeepLTranslationProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public DeepLTranslationProvider(
            DeepLTranslationProperties properties,
            ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .build();
    }

    @Override
    public TranslationOutput translate(TranslationInput input) {
        validateInput(input);
        validateApiKey();

        List<String> texts = input.description() == null
                ? List.of(input.title())
                : List.of(input.title(), input.description());

        byte[] requestBody = serialize(new DeepLRequest(texts, "EN", "ZH-HANS"));
        HttpResponse<byte[]> response = send(createRequest(requestBody));

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new TranslationException(
                    "DeepL translation request failed with HTTP " + response.statusCode()
            );
        }

        List<DeepLTranslation> translations = parse(response.body()).translations();
        int translationCount = translations == null ? 0 : translations.size();
        if (translationCount != texts.size()) {
            throw new TranslationException(
                    "DeepL returned " + translationCount
                            + " translations, but " + texts.size() + " were expected"
            );
        }
        if (translations.stream().anyMatch(translation -> translation == null || translation.text() == null)) {
            throw new TranslationException("DeepL returned a malformed translation response");
        }

        return new TranslationOutput(
                translations.getFirst().text(),
                input.description() == null ? null : translations.get(1).text()
        );
    }

    private void validateInput(TranslationInput input) {
        if (input == null) {
            throw new TranslationException("Translation input is required");
        }
        if (input.sourceLanguage() != SourceLanguage.EN
                || input.targetLanguage() != TranslationLanguage.ZH_CN) {
            throw new TranslationException("DeepL supports only EN to ZH_CN translation in V1");
        }
        if (input.title() == null) {
            throw new TranslationException("Translation title is required");
        }
    }

    private void validateApiKey() {
        if (properties.apiKey().isBlank()) {
            throw new TranslationException("DeepL API key is not configured");
        }
    }

    private byte[] serialize(DeepLRequest request) {
        try {
            return objectMapper.writeValueAsBytes(request);
        } catch (JsonProcessingException exception) {
            throw new TranslationException("Unable to create DeepL translation request", exception);
        }
    }

    private HttpRequest createRequest(byte[] requestBody) {
        URI endpoint;
        try {
            String separator = properties.baseUrl().endsWith("/") ? "" : "/";
            endpoint = URI.create(properties.baseUrl() + separator + "v2/translate");
        } catch (IllegalArgumentException exception) {
            throw new TranslationException("DeepL base URL is invalid", exception);
        }

        try {
            return HttpRequest.newBuilder(endpoint)
                    .timeout(REQUEST_TIMEOUT)
                    .header("Authorization", "DeepL-Auth-Key " + properties.apiKey())
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(requestBody))
                    .build();
        } catch (IllegalArgumentException exception) {
            throw new TranslationException("Unable to create DeepL translation request");
        }
    }

    private HttpResponse<byte[]> send(HttpRequest request) {
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new TranslationException("DeepL translation request was interrupted", exception);
        } catch (IOException exception) {
            throw new TranslationException("Unable to call DeepL translation API", exception);
        }
    }

    private DeepLResponse parse(byte[] responseBody) {
        try {
            DeepLResponse response = objectMapper.readValue(responseBody, DeepLResponse.class);
            if (response == null) {
                throw new TranslationException("Unable to parse DeepL translation response");
            }
            return response;
        } catch (IOException exception) {
            throw new TranslationException("Unable to parse DeepL translation response");
        }
    }

    private record DeepLRequest(
            List<String> text,
            @JsonProperty("source_lang") String sourceLanguage,
            @JsonProperty("target_lang") String targetLanguage
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record DeepLResponse(List<DeepLTranslation> translations) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record DeepLTranslation(String text) {
    }
}
