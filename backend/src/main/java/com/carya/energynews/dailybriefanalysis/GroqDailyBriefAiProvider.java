package com.carya.energynews.dailybriefanalysis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;

public class GroqDailyBriefAiProvider implements DailyBriefAiProvider {

    static final String PROVIDER_NAME = "groq";

    private static final URI DEFAULT_ENDPOINT = URI.create(
            "https://api.groq.com/openai/v1/chat/completions"
    );
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(60);

    private final String apiKey;
    private final String model;
    private final ObjectMapper objectMapper;
    private final DailyBriefAiPromptFactory promptFactory;
    private final URI endpoint;
    private final HttpClient httpClient;
    private final Duration requestTimeout;

    public GroqDailyBriefAiProvider(
            String apiKey,
            String model,
            ObjectMapper objectMapper,
            DailyBriefAiPromptFactory promptFactory
    ) {
        this(
                apiKey,
                model,
                objectMapper,
                promptFactory,
                DEFAULT_ENDPOINT,
                HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build(),
                REQUEST_TIMEOUT
        );
    }

    GroqDailyBriefAiProvider(
            String apiKey,
            String model,
            ObjectMapper objectMapper,
            DailyBriefAiPromptFactory promptFactory,
            URI endpoint,
            HttpClient httpClient,
            Duration requestTimeout
    ) {
        this.apiKey = requireText(apiKey, "Groq API key is required");
        this.model = requireText(model, "Groq model is required");
        this.objectMapper = Objects.requireNonNull(objectMapper, "Object mapper is required");
        this.promptFactory = Objects.requireNonNull(promptFactory, "Prompt factory is required");
        this.endpoint = Objects.requireNonNull(endpoint, "Groq endpoint is required");
        this.httpClient = Objects.requireNonNull(httpClient, "HTTP client is required");
        this.requestTimeout = Objects.requireNonNull(requestTimeout, "Request timeout is required");
    }

    @Override
    public String providerName() {
        return PROVIDER_NAME;
    }

    @Override
    public String model() {
        return model;
    }

    @Override
    public DailyBriefAiResult analyze(DailyBriefAiRequest request) {
        Objects.requireNonNull(request, "Daily brief AI request is required");
        HttpRequest httpRequest = createRequest(request);
        HttpResponse<String> response = send(httpRequest);
        validateStatus(response.statusCode());
        return parse(response.body());
    }

    private HttpRequest createRequest(DailyBriefAiRequest request) {
        try {
            String body = objectMapper.writeValueAsString(createRequestBody(request));
            return HttpRequest.newBuilder(endpoint)
                    .timeout(requestTimeout)
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json; charset=UTF-8")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw new DailyBriefAiException(
                    DailyBriefAiException.Failure.INVALID_REQUEST,
                    "Groq daily brief request could not be created",
                    exception
            );
        }
    }

    private ObjectNode createRequestBody(DailyBriefAiRequest request) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", model);
        body.put("reasoning_effort", "low");

        ArrayNode messages = body.putArray("messages");
        messages.addObject()
                .put("role", "system")
                .put("content", promptFactory.systemPrompt());
        messages.addObject()
                .put("role", "user")
                .put("content", promptFactory.userPrompt(request));

        ObjectNode jsonSchema = body.putObject("response_format")
                .put("type", "json_schema")
                .putObject("json_schema");
        jsonSchema.put("name", "daily_brief_analysis");
        jsonSchema.put("strict", true);
        jsonSchema.set("schema", createOutputSchema());
        return body;
    }

    private ObjectNode createOutputSchema() {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("type", "object");
        root.put("additionalProperties", false);
        root.putArray("required").add("headline").add("overview").add("events");

        ObjectNode properties = root.putObject("properties");
        properties.putObject("headline").put("type", "string");
        properties.putObject("overview").put("type", "string");

        ObjectNode events = properties.putObject("events");
        events.put("type", "array");
        events.put("minItems", 1);
        events.put("maxItems", 5);

        ObjectNode event = events.putObject("items");
        event.put("type", "object");
        event.put("additionalProperties", false);
        event.putArray("required")
                .add("title")
                .add("summary")
                .add("whyItMatters")
                .add("supportingArticleIds");

        ObjectNode eventProperties = event.putObject("properties");
        eventProperties.putObject("title").put("type", "string");
        eventProperties.putObject("summary").put("type", "string");
        eventProperties.putObject("whyItMatters").put("type", "string");
        ObjectNode supportingIds = eventProperties.putObject("supportingArticleIds");
        supportingIds.put("type", "array");
        supportingIds.put("minItems", 1);
        supportingIds.putObject("items").put("type", "integer");
        return root;
    }

    private HttpResponse<String> send(HttpRequest request) {
        try {
            return httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
            );
        } catch (HttpTimeoutException exception) {
            throw new DailyBriefAiException(
                    DailyBriefAiException.Failure.TIMEOUT,
                    "Groq daily brief request timed out",
                    exception
            );
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new DailyBriefAiException(
                    DailyBriefAiException.Failure.UPSTREAM,
                    "Groq daily brief request was interrupted",
                    exception
            );
        } catch (IOException exception) {
            throw new DailyBriefAiException(
                    DailyBriefAiException.Failure.UPSTREAM,
                    "Groq daily brief request failed",
                    exception
            );
        }
    }

    private void validateStatus(int status) {
        if (status >= 200 && status < 300) {
            return;
        }
        if (status == 401) {
            throw new DailyBriefAiException(
                    DailyBriefAiException.Failure.AUTHENTICATION,
                    "Groq daily brief authentication failed (HTTP 401)"
            );
        }
        if (status == 403) {
            throw new DailyBriefAiException(
                    DailyBriefAiException.Failure.AUTHORIZATION,
                    "Groq denied access to the daily brief request (HTTP 403)"
            );
        }
        if (status == 400 || status == 422) {
            throw new DailyBriefAiException(
                    DailyBriefAiException.Failure.INVALID_REQUEST,
                    "Groq rejected the daily brief request or structured-output configuration (HTTP "
                            + status + ")"
            );
        }
        if (status == 429) {
            throw new DailyBriefAiException(
                    DailyBriefAiException.Failure.RATE_LIMITED,
                    "Groq daily brief request was rate limited (HTTP 429)"
            );
        }
        throw new DailyBriefAiException(
                DailyBriefAiException.Failure.UPSTREAM,
                status >= 500
                        ? "Groq daily brief service is unavailable (HTTP " + status + ")"
                        : "Groq rejected the daily brief request (HTTP " + status + ")"
        );
    }

    private DailyBriefAiResult parse(String body) {
        try {
            JsonNode response = objectMapper.readTree(body);
            JsonNode choices = response == null ? null : response.get("choices");
            if (choices == null || !choices.isArray() || choices.isEmpty()) {
                throw malformedResponse();
            }
            JsonNode content = choices.get(0).path("message").get("content");
            if (content == null || !content.isTextual() || content.asText().isBlank()) {
                throw malformedResponse();
            }
            DailyBriefAiResult result = objectMapper.readValue(
                    content.asText(),
                    DailyBriefAiResult.class
            );
            if (result == null) {
                throw malformedResponse();
            }
            return result;
        } catch (JsonProcessingException exception) {
            throw new DailyBriefAiException(
                    DailyBriefAiException.Failure.MALFORMED_RESPONSE,
                    "Groq returned a malformed daily brief response",
                    exception
            );
        }
    }

    private DailyBriefAiException malformedResponse() {
        return new DailyBriefAiException(
                DailyBriefAiException.Failure.MALFORMED_RESPONSE,
                "Groq returned a malformed daily brief response"
        );
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.strip();
    }
}
