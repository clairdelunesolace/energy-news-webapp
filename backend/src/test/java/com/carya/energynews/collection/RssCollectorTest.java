package com.carya.energynews.collection;

import com.carya.energynews.source.Source;
import com.carya.energynews.source.SourcePriority;
import com.carya.energynews.source.SourceType;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RssCollectorTest {

    private HttpServer server;
    private RssCollector collector;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
        collector = new RssCollector();
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void collectsAnRssEntry() {
        serve("/feed", 200, """
                <?xml version="1.0" encoding="UTF-8"?>
                <rss version="2.0">
                  <channel>
                    <title>Energy News</title>
                    <link>https://example.com</link>
                    <description>Energy storage updates</description>
                    <item>
                      <title>Grid battery project announced</title>
                      <link>https://example.com/articles/grid-battery</link>
                      <description>A new grid-scale battery project.</description>
                      <pubDate>Tue, 18 Aug 2026 12:00:00 GMT</pubDate>
                    </item>
                  </channel>
                </rss>
                """);

        List<CollectedArticle> articles = collector.collect(rssSource("/feed"));

        assertThat(articles).containsExactly(new CollectedArticle(
                "Grid battery project announced",
                "https://example.com/articles/grid-battery",
                "A new grid-scale battery project.",
                null,
                Instant.parse("2026-08-18T12:00:00Z"),
                42L
        ));
    }

    @Test
    void collectsMultipleEntries() {
        serve("/multiple", 200, """
                <?xml version="1.0" encoding="UTF-8"?>
                <rss version="2.0">
                  <channel>
                    <title>Energy News</title>
                    <link>https://example.com</link>
                    <description>Energy storage updates</description>
                    <item>
                      <title>First article</title>
                      <link>https://example.com/articles/first</link>
                    </item>
                    <item>
                      <title>Second article</title>
                      <link>https://example.com/articles/second</link>
                    </item>
                  </channel>
                </rss>
                """);

        List<CollectedArticle> articles = collector.collect(rssSource("/multiple"));

        assertThat(articles).extracting(CollectedArticle::title)
                .containsExactly("First article", "Second article");
    }

    @Test
    void leavesPublishedAtNullWhenDateIsMissing() {
        serve("/no-date", 200, """
                <?xml version="1.0" encoding="UTF-8"?>
                <rss version="2.0">
                  <channel>
                    <title>Energy News</title>
                    <link>https://example.com</link>
                    <description>Energy storage updates</description>
                    <item>
                      <title>Undated article</title>
                      <link>https://example.com/articles/undated</link>
                    </item>
                  </channel>
                </rss>
                """);

        List<CollectedArticle> articles = collector.collect(rssSource("/no-date"));

        assertThat(articles).singleElement()
                .extracting(CollectedArticle::publishedAt)
                .isNull();
    }

    @Test
    void collectsAnAtomEntry() {
        serve("/atom", 200, """
                <?xml version="1.0" encoding="UTF-8"?>
                <feed xmlns="http://www.w3.org/2005/Atom">
                  <title>Energy News</title>
                  <id>https://example.com/atom</id>
                  <updated>2026-08-18T13:45:00Z</updated>
                  <entry>
                    <title>Atom battery article</title>
                    <link href="https://example.com/articles/atom-battery"/>
                    <id>https://example.com/articles/atom-battery</id>
                    <published>2026-08-18T13:45:00Z</published>
                    <summary>An Atom summary.</summary>
                  </entry>
                </feed>
                """);

        List<CollectedArticle> articles = collector.collect(rssSource("/atom"));

        assertThat(articles).containsExactly(new CollectedArticle(
                "Atom battery article",
                "https://example.com/articles/atom-battery",
                "An Atom summary.",
                null,
                Instant.parse("2026-08-18T13:45:00Z"),
                42L
        ));
    }

    @Test
    void preservesPlainTextDescription() throws IOException {
        assertThat(descriptionFromFixture("Plain text"))
                .isEqualTo("Battery storage costs fell 12%—developers’ outlook improved.");
    }

    @Test
    void removesUtilityDiveLikeFigureMarkup() throws IOException {
        assertThat(descriptionFromFixture("Utility Dive HTML"))
                .isEqualTo("Experts say battery storage will support the grid.");
    }

    @Test
    void normalizesMultipleHtmlParagraphsToReadableText() throws IOException {
        assertThat(descriptionFromFixture("Multiple paragraphs"))
                .isEqualTo("First summary paragraph. Second summary paragraph.");
    }

    @Test
    void decodesHtmlEntities() throws IOException {
        assertThat(descriptionFromFixture("HTML entities"))
                .isEqualTo("Storage R&D is developers' priority.");
    }

    @Test
    void returnsNullForNonReadableMarkupOnlyDescription() throws IOException {
        assertThat(descriptionFromFixture("Non-readable markup only")).isNull();
    }

    @Test
    void preservesChineseAndUnicodeDescription() throws IOException {
        assertThat(descriptionFromFixture("Chinese Unicode"))
                .isEqualTo("储能项目“如期投运”，容量为 200 兆瓦时。");
    }

    @Test
    void rejectsNonRssSources() {
        Source source = new Source(
                "API source",
                feedUrl("/unused"),
                SourceType.API,
                SourcePriority.MEDIUM
        );

        assertThatThrownBy(() -> collector.collect(source))
                .isInstanceOf(NewsCollectionException.class)
                .hasMessage("RssCollector requires SourceType.RSS but received API");
    }

    @Test
    void reportsMalformedFeeds() {
        serve("/malformed", 200, "<rss><channel><item></rss>");

        assertThatThrownBy(() -> collector.collect(rssSource("/malformed")))
                .isInstanceOf(NewsCollectionException.class)
                .hasMessageStartingWith("Unable to parse RSS feed from ");
    }

    @Test
    void reportsUnavailableFeeds() {
        serve("/unavailable", 503, "Service unavailable");

        assertThatThrownBy(() -> collector.collect(rssSource("/unavailable")))
                .isInstanceOf(NewsCollectionException.class)
                .hasMessageContaining("RSS feed request failed with HTTP 503");
    }

    @Test
    void reportsUnreachableFeeds() {
        Source source = rssSource("/unreachable");
        server.stop(0);
        server = null;

        assertThatThrownBy(() -> collector.collect(source))
                .isInstanceOf(NewsCollectionException.class)
                .hasMessageStartingWith("Unable to fetch RSS feed from ");
    }

    private Source rssSource(String path) {
        Source source = new Source(
                "RSS source",
                feedUrl(path),
                SourceType.RSS,
                SourcePriority.MEDIUM
        );
        ReflectionTestUtils.setField(source, "id", 42L);
        return source;
    }

    private String descriptionFromFixture(String title) throws IOException {
        String path = "/description-normalization";
        serve(path, 200, readFixture("rss-description-normalization.xml"));
        return collector.collect(rssSource(path)).stream()
                .filter(article -> article.title().equals(title))
                .findFirst()
                .orElseThrow()
                .description();
    }

    private String readFixture(String name) throws IOException {
        try (InputStream input = getClass().getResourceAsStream("/fixtures/" + name)) {
            if (input == null) {
                throw new IOException("Missing RSS fixture: " + name);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private String feedUrl(String path) {
        return "http://127.0.0.1:" + server.getAddress().getPort() + path;
    }

    private void serve(String path, int status, String response) {
        server.createContext(path, exchange -> {
            byte[] body = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/xml; charset=UTF-8");
            exchange.sendResponseHeaders(status, body.length);
            try (var output = exchange.getResponseBody()) {
                output.write(body);
            }
        });
    }
}
