package com.carya.energynews.collection;

import com.carya.energynews.source.Source;
import com.carya.energynews.source.SourceType;
import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.FeedException;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

@Component
public class RssCollector implements NewsCollector {

    private static final String NON_SUMMARY_ELEMENTS =
            "figure, img, picture, source, script, style, iframe, form, button, noscript";

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    @Override
    public List<CollectedArticle> collect(Source source) {
        validateSource(source);

        HttpRequest request = createRequest(source);
        HttpResponse<byte[]> response = fetch(request, source.getUrl());

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new NewsCollectionException(
                    "RSS feed request failed with HTTP " + response.statusCode() + " for " + source.getUrl()
            );
        }

        SyndFeed feed = parse(response.body(), source.getUrl());
        return feed.getEntries().stream()
                .map(entry -> toCollectedArticle(entry, source.getId()))
                .toList();
    }

    private void validateSource(Source source) {
        if (source == null) {
            throw new NewsCollectionException("RssCollector requires a Source");
        }
        if (source.getType() != SourceType.RSS) {
            throw new NewsCollectionException(
                    "RssCollector requires SourceType.RSS but received " + source.getType()
            );
        }
    }

    private HttpRequest createRequest(Source source) {
        try {
            return HttpRequest.newBuilder(URI.create(source.getUrl()))
                    .timeout(Duration.ofSeconds(30))
                    .header("Accept", "application/rss+xml, application/atom+xml, application/xml, text/xml")
                    .GET()
                    .build();
        } catch (IllegalArgumentException exception) {
            throw new NewsCollectionException("Invalid RSS feed URL: " + source.getUrl(), exception);
        }
    }

    private HttpResponse<byte[]> fetch(HttpRequest request, String sourceUrl) {
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new NewsCollectionException("RSS feed request was interrupted for " + sourceUrl, exception);
        } catch (IOException exception) {
            throw new NewsCollectionException("Unable to fetch RSS feed from " + sourceUrl, exception);
        }
    }

    private SyndFeed parse(byte[] feedContent, String sourceUrl) {
        try {
            return new SyndFeedInput().build(new XmlReader(new ByteArrayInputStream(feedContent)));
        } catch (FeedException | IOException exception) {
            throw new NewsCollectionException("Unable to parse RSS feed from " + sourceUrl, exception);
        }
    }

    private CollectedArticle toCollectedArticle(SyndEntry entry, Long sourceId) {
        return new CollectedArticle(
                entry.getTitle(),
                entry.getLink(),
                normalizeDescription(
                        entry.getDescription() == null
                                ? null
                                : entry.getDescription().getValue()
                ),
                null,
                entry.getPublishedDate() == null ? null : entry.getPublishedDate().toInstant(),
                sourceId
        );
    }

    private String normalizeDescription(String description) {
        if (description == null) {
            return null;
        }

        Document document = Jsoup.parseBodyFragment(description);
        document.select(NON_SUMMARY_ELEMENTS).remove();
        String readableText = document.body().text().trim();
        return readableText.isBlank() ? null : readableText;
    }
}
