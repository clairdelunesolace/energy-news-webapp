package com.carya.energynews.discovery;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
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
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class BraveNewsDiscoveryProvider implements NewsDiscoveryProvider {

    static final String PROVIDER_NAME = "brave-news";

    private static final URI NEWS_SEARCH_ENDPOINT = URI.create(
            "https://api.search.brave.com/res/v1/news/search"
    );
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(20);
    private static final int MAX_RESULTS_PER_PAGE = 50;
    private static final int MAX_QUERY_CHARACTERS = 400;
    private static final int MAX_QUERY_WORDS = 50;

    private final String apiKey;
    private final ObjectMapper objectMapper;
    private final URI endpoint;
    private final HttpClient httpClient;
    private final Duration requestTimeout;

    public BraveNewsDiscoveryProvider(String apiKey, ObjectMapper objectMapper) {
        this(
                apiKey,
                objectMapper,
                NEWS_SEARCH_ENDPOINT,
                HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build(),
                REQUEST_TIMEOUT
        );
    }

    BraveNewsDiscoveryProvider(
            String apiKey,
            ObjectMapper objectMapper,
            URI endpoint,
            HttpClient httpClient,
            Duration requestTimeout
    ) {
        this.apiKey = requireApiKey(apiKey);
        this.objectMapper = Objects.requireNonNull(objectMapper, "Object mapper is required");
        this.endpoint = Objects.requireNonNull(endpoint, "Brave endpoint is required");
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
        String freshness = freshness(query);

        List<DiscoveredArticle> discovered = new ArrayList<>();
        int requested = 0;
        int offset = 0;
        while (requested < query.limit()) {
            int count = Math.min(MAX_RESULTS_PER_PAGE, query.limit() - requested);
            BraveResponse response = fetchPage(query.keyword(), freshness, count, offset);
            List<BraveResult> candidates = response.results() == null
                    ? List.of()
                    : response.results();

            int discoveredBeforePage = discovered.size();
            for (BraveResult candidate : candidates) {
                DiscoveredArticle article = map(candidate);
                if (article != null) {
                    discovered.add(article);
                    if (discovered.size() == query.limit()) {
                        return List.copyOf(discovered);
                    }
                }
            }

            requested += count;
            if (candidates.size() < count || discovered.size() == discoveredBeforePage) {
                break;
            }
            offset++;
        }
        return List.copyOf(discovered);
    }

    private BraveResponse fetchPage(String keyword, String freshness, int count, int offset) {
        HttpRequest request = createRequest(keyword, freshness, count, offset);

        HttpResponse<byte[]> response = send(request, keyword);
        validateStatus(response.statusCode(), keyword);
        return parse(response.body(), keyword);
    }

    private HttpRequest createRequest(String keyword, String freshness, int count, int offset) {
        try {
            return HttpRequest.newBuilder(createRequestUri(keyword, freshness, count, offset))
                    .timeout(requestTimeout)
                    .header("Accept", "application/json")
                    .header("X-Subscription-Token", apiKey)
                    .GET()
                    .build();
        } catch (IllegalArgumentException exception) {
            throw new NewsDiscoveryException(
                    PROVIDER_NAME + " could not create a request for keyword '" + keyword + "'",
                    exception
            );
        }
    }

    private URI createRequestUri(String keyword, String freshness, int count, int offset) {
        StringBuilder query = new StringBuilder()
                .append("q=").append(encode(keyword))
                .append("&country=ALL")
                .append("&count=").append(count)
                .append("&offset=").append(offset);
        if (freshness != null) {
            query.append("&freshness=").append(encode(freshness));
        }

        try {
            return URI.create(endpoint + "?" + query);
        } catch (IllegalArgumentException exception) {
            throw new NewsDiscoveryException(
                    PROVIDER_NAME + " could not create a request for keyword '" + keyword + "'",
                    exception
            );
        }
    }

    private HttpResponse<byte[]> send(HttpRequest request, String keyword) {
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        } catch (HttpTimeoutException exception) {
            throw new NewsDiscoveryException(
                    PROVIDER_NAME + " discovery timed out for keyword '" + keyword + "'",
                    exception
            );
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new NewsDiscoveryException(
                    PROVIDER_NAME + " discovery was interrupted for keyword '" + keyword + "'",
                    exception
            );
        } catch (IOException exception) {
            throw new NewsDiscoveryException(
                    PROVIDER_NAME + " discovery request failed for keyword '" + keyword + "'",
                    exception
            );
        }
    }

    private void validateStatus(int status, String keyword) {
        if (status >= 200 && status < 300) {
            return;
        }

        String context = " (HTTP " + status + ") for keyword '" + keyword + "'";
        if (status == 401 || status == 403) {
            throw new NewsDiscoveryException(PROVIDER_NAME + " authentication failed" + context);
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

    private BraveResponse parse(byte[] body, String keyword) {
        try {
            BraveResponse response = objectMapper.readValue(body, BraveResponse.class);
            if (response == null) {
                throw new NewsDiscoveryException(
                        PROVIDER_NAME + " returned an invalid response for keyword '" + keyword + "'"
                );
            }
            return response;
        } catch (IOException exception) {
            throw new NewsDiscoveryException(
                    PROVIDER_NAME + " returned invalid JSON for keyword '" + keyword + "'",
                    exception
            );
        }
    }

    private DiscoveredArticle map(BraveResult result) {
        if (result == null || isBlank(result.title()) || isBlank(result.url())) {
            return null;
        }

        return new DiscoveredArticle(
                result.title(),
                result.url(),
                optionalText(result.description()),
                sourceName(result),
                publishedAt(result)
        );
    }

    private String sourceName(BraveResult result) {
        if (result.profile() != null) {
            String source = optionalText(result.profile().longName());
            if (source != null) {
                return source;
            }
            source = optionalText(result.profile().name());
            if (source != null) {
                return source;
            }
        }
        if (result.metaUrl() != null) {
            String hostname = optionalText(result.metaUrl().hostname());
            if (hostname != null) {
                return hostname;
            }
        }
        try {
            return optionalText(URI.create(result.url()).getHost());
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private Instant publishedAt(BraveResult result) {
        Instant pageAge = parseInstant(result.pageAge());
        return pageAge != null ? pageAge : parseInstant(result.age());
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

    private String freshness(NewsDiscoveryQuery query) {
        if (query.from() == null && query.to() == null) {
            return null;
        }
        if (query.from() == null || query.to() == null) {
            throw new NewsDiscoveryException(
                    PROVIDER_NAME + " requires both from and to dates for discovery freshness"
            );
        }
        return query.from().atZone(ZoneOffset.UTC).toLocalDate()
                + "to"
                + query.to().atZone(ZoneOffset.UTC).toLocalDate();
    }

    private void validateKeyword(String keyword) {
        int characters = keyword.codePointCount(0, keyword.length());
        int words = keyword.split("\\s+").length;
        if (characters > MAX_QUERY_CHARACTERS || words > MAX_QUERY_WORDS) {
            throw new NewsDiscoveryException(
                    PROVIDER_NAME + " keyword exceeds Brave's 400 character or 50 word limit"
            );
        }
    }

    private static String requireApiKey(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("Brave Search API key is required");
        }
        return apiKey.strip();
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
    private record BraveResponse(List<BraveResult> results) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record BraveResult(
            String title,
            String url,
            String description,
            String age,
            @JsonProperty("page_age") String pageAge,
            @JsonProperty("meta_url") BraveMetaUrl metaUrl,
            BraveProfile profile
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record BraveMetaUrl(String hostname) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record BraveProfile(
            String name,
            @JsonProperty("long_name") String longName
    ) {
    }
}
