package com.carya.energynews.content;

import com.carya.energynews.article.Article;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Locale;

/**
 * Fetches static article HTML only. Callers must use it only where publisher access and usage
 * terms permit; it does not execute JavaScript or bypass access controls.
 */
@Component
public class WebArticleContentFetcher implements ArticleContentFetcher {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);
    private static final int MIN_MEANINGFUL_PARAGRAPH_LENGTH = 20;
    private static final int MIN_MEANINGFUL_PARAGRAPH_COUNT = 2;
    private static final int MIN_CONTENT_LENGTH = 200;
    private static final String UNWANTED_ELEMENTS = """
            script, style, noscript, nav, footer, aside, form,
            [role=navigation], [role=contentinfo],
            .cookie-banner, .cookie-consent, .related-articles, .newsletter, .advertisement
            """;
    private static final List<String> BODY_SELECTORS = List.of(
            "[itemprop=articleBody]",
            ".article-body",
            ".article__body",
            ".article-content",
            ".entry-content",
            ".post-content",
            ".story-body",
            "article",
            "main"
    );

    private final HttpClient httpClient;

    public WebArticleContentFetcher() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @Override
    public String fetchContent(Article article) {
        String articleUrl = validateAndGetUrl(article);
        HttpResponse<byte[]> response = send(createRequest(articleUrl), articleUrl);

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new ArticleContentFetchException(
                    "Article content request failed with HTTP " + response.statusCode()
            );
        }

        return extractContent(response.body(), articleUrl);
    }

    private String validateAndGetUrl(Article article) {
        if (article == null || article.getUrl() == null || article.getUrl().isBlank()) {
            throw new ArticleContentFetchException("Article URL is required");
        }

        try {
            URI uri = URI.create(article.getUrl());
            String scheme = uri.getScheme();
            String normalizedScheme = scheme == null ? null : scheme.toLowerCase(Locale.ROOT);
            if (scheme == null
                    || !(normalizedScheme.equals("http") || normalizedScheme.equals("https"))) {
                throw new ArticleContentFetchException("Article URL must use HTTP or HTTPS");
            }
            return article.getUrl();
        } catch (IllegalArgumentException exception) {
            throw new ArticleContentFetchException("Article URL is invalid", exception);
        }
    }

    private HttpRequest createRequest(String articleUrl) {
        try {
            return HttpRequest.newBuilder(URI.create(articleUrl))
                    .timeout(REQUEST_TIMEOUT)
                    .header("Accept", "text/html,application/xhtml+xml")
                    .header("User-Agent", "EnergyNewsContentFetcher/1.0")
                    .GET()
                    .build();
        } catch (IllegalArgumentException exception) {
            throw new ArticleContentFetchException("Unable to create article content request", exception);
        }
    }

    private HttpResponse<byte[]> send(HttpRequest request, String articleUrl) {
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ArticleContentFetchException("Article content request was interrupted", exception);
        } catch (IOException exception) {
            throw new ArticleContentFetchException(
                    "Unable to fetch article content from " + articleUrl,
                    exception
            );
        }
    }

    private String extractContent(byte[] responseBody, String articleUrl) {
        Document document;
        try {
            document = Jsoup.parse(new ByteArrayInputStream(responseBody), null, articleUrl);
        } catch (IOException exception) {
            throw new ArticleContentFetchException("Unable to parse article HTML", exception);
        }

        document.select(UNWANTED_ELEMENTS).remove();
        for (String selector : BODY_SELECTORS) {
            for (Element container : document.select(selector)) {
                List<String> paragraphs = extractParagraphs(container);
                if (isUsable(paragraphs)) {
                    return String.join("\n\n", paragraphs);
                }
            }
        }

        throw new ArticleContentFetchException("No usable article content was found");
    }

    private List<String> extractParagraphs(Element container) {
        return container.select("p").stream()
                .map(Element::text)
                .map(String::trim)
                .filter(text -> !text.isBlank())
                .distinct()
                .toList();
    }

    private boolean isUsable(List<String> paragraphs) {
        long meaningfulParagraphCount = paragraphs.stream()
                .filter(paragraph -> paragraph.length() >= MIN_MEANINGFUL_PARAGRAPH_LENGTH)
                .count();
        int contentLength = paragraphs.stream()
                .mapToInt(String::length)
                .sum();
        return meaningfulParagraphCount >= MIN_MEANINGFUL_PARAGRAPH_COUNT
                && contentLength >= MIN_CONTENT_LENGTH;
    }
}
