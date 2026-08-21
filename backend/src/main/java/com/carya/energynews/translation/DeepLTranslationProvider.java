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
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Component
public class DeepLTranslationProvider implements TranslationProvider {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);
    private static final int MAX_REQUEST_BODY_BYTES = 64 * 1024;
    private static final int MAX_CONTENT_SEGMENT_BYTES = 32 * 1024;
    private static final int MAX_TEXTS_PER_REQUEST = 50;

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

        List<String> shortTexts = new ArrayList<>();
        if (input.title() != null) {
            shortTexts.add(input.title());
        }
        if (input.description() != null) {
            shortTexts.add(input.description());
        }

        List<String> shortTranslations = translateTexts(shortTexts);
        int shortTranslationIndex = 0;
        String translatedTitle = input.title() == null
                ? null
                : shortTranslations.get(shortTranslationIndex++);
        String translatedDescription = input.description() == null
                ? null
                : shortTranslations.get(shortTranslationIndex);
        String translatedContent = input.content() == null
                ? null
                : translateContent(input.content());

        return new TranslationOutput(
                translatedTitle,
                translatedDescription,
                translatedContent
        );
    }

    private List<String> translateTexts(List<String> texts) {
        if (texts.isEmpty()) {
            return List.of();
        }

        byte[] requestBody = serialize(new DeepLRequest(texts, "EN", "ZH-HANS"));
        if (requestBody.length > MAX_REQUEST_BODY_BYTES) {
            throw new TranslationException("DeepL translation request exceeds the safe size limit");
        }

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

        return translations.stream()
                .map(DeepLTranslation::text)
                .toList();
    }

    private String translateContent(String content) {
        List<ContentSegment> segments = segmentContent(content);
        if (segments.isEmpty()) {
            return "";
        }

        int paragraphCount = segments.getLast().paragraphIndex() + 1;
        List<StringBuilder> translatedParagraphs = new ArrayList<>(paragraphCount);
        for (int index = 0; index < paragraphCount; index++) {
            translatedParagraphs.add(new StringBuilder());
        }

        for (List<ContentSegment> batch : batchContentSegments(segments)) {
            List<String> translatedSegments = translateTexts(
                    batch.stream().map(ContentSegment::text).toList()
            );
            for (int index = 0; index < batch.size(); index++) {
                ContentSegment segment = batch.get(index);
                translatedParagraphs.get(segment.paragraphIndex())
                        .append(translatedSegments.get(index));
            }
        }

        return String.join(
                "\n\n",
                translatedParagraphs.stream().map(StringBuilder::toString).toList()
        );
    }

    private List<ContentSegment> segmentContent(String content) {
        List<ContentSegment> segments = new ArrayList<>();
        int paragraphIndex = 0;
        for (String rawParagraph : content.split("(?:\\R\\s*){2,}")) {
            String paragraph = rawParagraph.trim();
            if (paragraph.isBlank()) {
                continue;
            }
            for (String segment : splitOversizedParagraph(paragraph)) {
                segments.add(new ContentSegment(paragraphIndex, segment));
            }
            paragraphIndex++;
        }
        return segments;
    }

    private List<String> splitOversizedParagraph(String paragraph) {
        List<String> segments = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int currentBytes = 0;

        for (int offset = 0; offset < paragraph.length();) {
            int codePoint = paragraph.codePointAt(offset);
            int codePointBytes = utf8Length(codePoint);
            if (!current.isEmpty()
                    && currentBytes + codePointBytes > MAX_CONTENT_SEGMENT_BYTES) {
                segments.add(current.toString());
                current.setLength(0);
                currentBytes = 0;
            }
            current.appendCodePoint(codePoint);
            currentBytes += codePointBytes;
            offset += Character.charCount(codePoint);
        }

        if (!current.isEmpty()) {
            segments.add(current.toString());
        }
        return segments;
    }

    private int utf8Length(int codePoint) {
        return new String(Character.toChars(codePoint)).getBytes(StandardCharsets.UTF_8).length;
    }

    private List<List<ContentSegment>> batchContentSegments(List<ContentSegment> segments) {
        List<List<ContentSegment>> batches = new ArrayList<>();
        List<ContentSegment> currentBatch = new ArrayList<>();

        for (ContentSegment segment : segments) {
            List<ContentSegment> candidate = new ArrayList<>(currentBatch);
            candidate.add(segment);
            if (!currentBatch.isEmpty() && !fitsInOneRequest(candidate)) {
                batches.add(List.copyOf(currentBatch));
                currentBatch.clear();
            }
            currentBatch.add(segment);
            if (!fitsInOneRequest(currentBatch)) {
                throw new TranslationException(
                        "DeepL content segment exceeds the safe request size limit"
                );
            }
        }

        if (!currentBatch.isEmpty()) {
            batches.add(List.copyOf(currentBatch));
        }
        return batches;
    }

    private boolean fitsInOneRequest(List<ContentSegment> segments) {
        if (segments.size() > MAX_TEXTS_PER_REQUEST) {
            return false;
        }
        List<String> texts = segments.stream().map(ContentSegment::text).toList();
        return serialize(new DeepLRequest(texts, "EN", "ZH-HANS")).length
                <= MAX_REQUEST_BODY_BYTES;
    }

    private void validateInput(TranslationInput input) {
        if (input == null) {
            throw new TranslationException("Translation input is required");
        }
        if (input.sourceLanguage() != SourceLanguage.EN
                || input.targetLanguage() != TranslationLanguage.ZH_CN) {
            throw new TranslationException("DeepL supports only EN to ZH_CN translation in V1");
        }
        if (input.title() == null && input.description() == null && input.content() == null) {
            throw new TranslationException("At least one translation text is required");
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

    private record ContentSegment(int paragraphIndex, String text) {
    }
}
