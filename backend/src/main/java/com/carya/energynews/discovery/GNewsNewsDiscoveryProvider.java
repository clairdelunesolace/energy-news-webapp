package com.carya.energynews.discovery;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class GNewsNewsDiscoveryProvider implements NewsDiscoveryProvider {

    static final String PROVIDER_NAME = "gnews";

    private static final URI SEARCH_ENDPOINT = URI.create("https://gnews.io/api/v4/search");
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(20);
    private static final int MAX_QUERY_CHARACTERS = 200;

    private final String apiKey;
    private final int maxResultsPerRequest;
    private final ObjectMapper objectMapper;
    private final URI endpoint;
    private final HttpClient httpClient;
    private final Duration requestTimeout;

    public GNewsNewsDiscoveryProvider(
            String apiKey,
            int maxResultsPerRequest,
            ObjectMapper objectMapper
    ) {
        this(
                apiKey,
                maxResultsPerRequest,
                objectMapper,
                SEARCH_ENDPOINT,
                HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build(),
                REQUEST_TIMEOUT
        );
    }

    GNewsNewsDiscoveryProvider(
            String apiKey,
            int maxResultsPerRequest,
            ObjectMapper objectMapper,
            URI endpoint,
            HttpClient httpClient,
            Duration requestTimeout
    ) {
        this.apiKey = requireApiKey(apiKey);
        this.maxResultsPerRequest = requirePageSize(maxResultsPerRequest);
        this.objectMapper = Objects.requireNonNull(objectMapper, "Object mapper is required");
        this.endpoint = Objects.requireNonNull(endpoint, "GNews endpoint is required");
        this.httpClient = Objects.requireNonNull(httpClient, "HTTP client is required");
        this.requestTimeout = Objects.requireNonNull(requestTimeout, "Request timeout is required");
    }

    @Override
    public String providerName() {
        return PROVIDER_NAME;
    }

    @Override
    public List<DiscoveredArticle> discover(NewsDiscoveryQuery query) {
        Objects.requireNonNull(query, "Discovery query is required");
        validateKeyword(query.keyword());

        List<DiscoveredArticle> discovered = new ArrayList<>();
        int requested = 0;
        int page = 1;
        while (requested < query.limit()) {
            int pageSize = Math.min(maxResultsPerRequest, query.limit() - requested);
            GNewsSearchResponse response = fetchPage(query, pageSize, page);
            List<GNewsArticle> candidates = response.articles() == null
                    ? List.of()
                    : response.articles();

            int discoveredBeforePage = discovered.size();
            for (GNewsArticle candidate : candidates) {
                DiscoveredArticle article = map(candidate);
                if (article != null) {
                    discovered.add(article);
                    if (discovered.size() == query.limit()) {
                        return List.copyOf(discovered);
                    }
                }
            }

            requested += pageSize;
            if (candidates.size() < pageSize
                    || discovered.size() == discoveredBeforePage
                    || noMoreResults(response.totalArticles(), requested)) {
                break;
            }
            page++;
        }
        return List.copyOf(discovered);
    }

    private GNewsSearchResponse fetchPage(NewsDiscoveryQuery query, int pageSize, int page) {
        HttpRequest request = createRequest(query, pageSize, page);
        HttpResponse<byte[]> response = send(request, query.keyword());
        validateStatus(response.statusCode(), query.keyword());
        return parse(response.body(), query.keyword());
    }

    private HttpRequest createRequest(NewsDiscoveryQuery query, int pageSize, int page) {
        try {
            return HttpRequest.newBuilder(createRequestUri(query, pageSize, page))
                    .timeout(requestTimeout)
                    .header("Accept", "application/json")
                    .GET()
                    .build();
        } catch (IllegalArgumentException exception) {
            throw new NewsDiscoveryException(
                    PROVIDER_NAME + " could not create a request for keyword '"
                            + query.keyword() + "'"
            );
        }
    }

    private URI createRequestUri(NewsDiscoveryQuery query, int pageSize, int page) {
        StringBuilder parameters = new StringBuilder()
                .append("q=").append(encode(query.keyword()))
                .append("&apikey=").append(encode(apiKey))
                .append("&max=").append(pageSize)
                .append("&page=").append(page)
                .append("&sortby=publishedAt");
        if (query.from() != null) {
            parameters.append("&from=").append(encode(query.from().toString()));
        }
        if (query.to() != null) {
            parameters.append("&to=").append(encode(query.to().toString()));
        }

        try {
            return URI.create(endpoint + "?" + parameters);
        } catch (IllegalArgumentException exception) {
            throw new NewsDiscoveryException(
                    PROVIDER_NAME + " could not create a request for keyword '"
                            + query.keyword() + "'"
            );
        }
    }

    private HttpResponse<byte[]> send(HttpRequest request, String keyword) {
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        } catch (HttpTimeoutException exception) {
            throw new NewsDiscoveryException(
                    PROVIDER_NAME + " discovery timed out for keyword '" + keyword + "'"
            );
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new NewsDiscoveryException(
                    PROVIDER_NAME + " discovery was interrupted for keyword '" + keyword + "'"
            );
        } catch (IOException exception) {
            throw new NewsDiscoveryException(
                    PROVIDER_NAME + " discovery request failed for keyword '" + keyword + "'"
            );
        }
    }

    private void validateStatus(int status, String keyword) {
        if (status >= 200 && status < 300) {
            return;
        }

        String context = " (HTTP " + status + ") for keyword '" + keyword + "'";
        if (status == 401) {
            throw new NewsDiscoveryException(PROVIDER_NAME + " authentication failed" + context);
        }
        if (status == 403) {
            throw new NewsDiscoveryException(PROVIDER_NAME + " quota was exceeded" + context);
        }
        if (status == 429) {
            throw new NewsDiscoveryException(PROVIDER_NAME + " was rate limited" + context);
        }
        if (status >= 400 && status < 500) {
            throw new NewsDiscoveryException(PROVIDER_NAME + " rejected the request" + context);
        }
        if (status >= 500) {
            throw new NewsDiscoveryException(PROVIDER_NAME + " is unavailable" + context);
        }
        throw new NewsDiscoveryException(PROVIDER_NAME + " returned an unexpected status" + context);
    }

    private GNewsSearchResponse parse(byte[] body, String keyword) {
        try {
            GNewsSearchResponse response = objectMapper.readValue(body, GNewsSearchResponse.class);
            if (response == null) {
                throw new NewsDiscoveryException(
                        PROVIDER_NAME + " returned an invalid response for keyword '" + keyword + "'"
                );
            }
            return response;
        } catch (IOException exception) {
            throw new NewsDiscoveryException(
                    PROVIDER_NAME + " returned invalid JSON for keyword '" + keyword + "'"
            );
        }
    }

    private DiscoveredArticle map(GNewsArticle article) {
        if (article == null || isBlank(article.title()) || isBlank(article.url())) {
            return null;
        }
        return new DiscoveredArticle(
                article.title(),
                article.url(),
                optionalText(article.description()),
                article.source() == null ? null : optionalText(article.source().name()),
                parseInstant(article.publishedAt()),
                optionalText(article.lang())
        );
    }

    private Instant parseInstant(String value) {
        String normalized = optionalText(value);
        if (normalized == null) {
            return null;
        }
        try {
            return Instant.parse(normalized);
        } catch (DateTimeParseException exception) {
            return null;
        }
    }

    private boolean noMoreResults(Long totalArticles, int requested) {
        return totalArticles != null && requested >= totalArticles;
    }

    private void validateKeyword(String keyword) {
        if (keyword.codePointCount(0, keyword.length()) > MAX_QUERY_CHARACTERS) {
            throw new NewsDiscoveryException(
                    PROVIDER_NAME + " keyword exceeds GNews's 200 character limit"
            );
        }
    }

    private static String requireApiKey(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("GNews API key is required");
        }
        return apiKey.strip();
    }

    private static int requirePageSize(int maxResultsPerRequest) {
        if (maxResultsPerRequest < 1 || maxResultsPerRequest > 100) {
            throw new IllegalArgumentException(
                    "GNews max results per request must be between 1 and 100"
            );
        }
        return maxResultsPerRequest;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String optionalText(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.strip();
        return normalized.isBlank() ? null : normalized;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record GNewsSearchResponse(
            Long totalArticles,
            List<GNewsArticle> articles
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record GNewsArticle(
            String title,
            String url,
            String description,
            String publishedAt,
            String lang,
            GNewsSource source
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record GNewsSource(String name) {
    }
}
