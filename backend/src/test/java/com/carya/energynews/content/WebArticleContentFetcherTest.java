package com.carya.energynews.content;

import com.carya.energynews.article.Article;
import com.carya.energynews.source.Source;
import com.carya.energynews.source.SourcePriority;
import com.carya.energynews.source.SourceType;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WebArticleContentFetcherTest {

    private HttpServer server;
    private WebArticleContentFetcher fetcher;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
        fetcher = new WebArticleContentFetcher();
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void extractsReadableParagraphsAndIgnoresCommonPageChrome() {
        String firstParagraph = "Battery cell production at the new Michigan factory will support "
                + "electric vehicles and stationary energy-storage projects across the region.";
        String secondParagraph = "The company said the expanded manufacturing line will create "
                + "skilled jobs while strengthening the domestic battery supply chain.";
        serve(200, """
                <html><body>
                  <nav><p>Site navigation</p></nav>
                  <article>
                    <p>%s</p>
                    <script>tracking()</script>
                    <style>.hidden { display: none; }</style>
                    <p>%s</p>
                    <p>%s</p>
                    <aside><p>Related story</p></aside>
                  </article>
                  <footer><p>Copyright notice</p></footer>
                </body></html>
                """.formatted(firstParagraph, secondParagraph, secondParagraph));

        String content = fetcher.fetchContent(article(url("/article")));

        assertThat(content).isEqualTo(firstParagraph + "\n\n" + secondParagraph);
        assertThat(content).doesNotContain(
                "Site navigation",
                "tracking",
                "Related story",
                "Copyright notice"
        );
    }

    @Test
    void usesMainAsAConservativeFallback() {
        String firstParagraph = "Grid operators are adding long-duration battery systems to "
                + "manage evening demand and absorb growing amounts of renewable electricity.";
        String secondParagraph = "Developers expect the next project phase to provide several "
                + "hours of reliable capacity during periods of peak demand.";
        serve(200, "<html><main><p>" + firstParagraph + "</p><p>"
                + secondParagraph + "</p></main></html>");

        assertThat(fetcher.fetchContent(article(url("/article"))))
                .isEqualTo(firstParagraph + "\n\n" + secondParagraph);
    }

    @Test
    void rejectsTinyCandidateAndFallsThroughToValidLaterBody() throws IOException {
        serve(200, fixture("canary-content-fallback.html"));

        String content = fetcher.fetchContent(article(url("/article")));

        assertThat(content)
                .startsWith("LG Energy Solution has opened a major battery cell factory")
                .contains("\n\nThe Michigan facility is designed")
                .contains("\n\nCompany leaders said")
                .doesNotContain("Next Upcoming", "By Canary Media");
    }

    @Test
    void excludesTrailingPublisherChromeFromArticleBody() throws IOException {
        serve(200, fixture("canary-trailing-chrome.html"));

        String content = fetcher.fetchContent(article(url("/article")));

        assertThat(content)
                .isEqualTo(
                        "LG Energy Solution opened its Michigan battery cell factory after "
                                + "converting part of the facility to serve fast-growing grid "
                                + "energy-storage demand.\n\n"
                                + "Company leaders said the plant will strengthen domestic supply "
                                + "chains, support skilled manufacturing jobs, and supply lithium "
                                + "iron phosphate cells to storage projects across North America.\n\n"
                                + "The factory is expected to reach full production next year as "
                                + "utilities and developers add batteries to meet rising electricity "
                                + "demand and balance renewable generation."
                )
                .doesNotContain(
                        "signup form",
                        "cookie consent",
                        "newsletter subscription",
                        "freelance reporter",
                        "Geothermal",
                        "Clean energy manufacturing",
                        "Politics"
                );
    }

    @Test
    void keepsLegitimateParagraphsThatMentionStructuralCleanupTerms() {
        String firstParagraph = "The policy analysis discusses politics and public consent while "
                + "explaining why the proposed battery project remains important for local grid "
                + "reliability and long-term energy planning.";
        String secondParagraph = "The utility also described subscription options and browser "
                + "cookies in its customer portal without changing the technical design, operating "
                + "schedule, or expected performance of the storage facility.";
        serve(200, "<html><article><p>" + firstParagraph + "</p><p>"
                + secondParagraph + "</p></article></html>");

        assertThat(fetcher.fetchContent(article(url("/article"))))
                .isEqualTo(firstParagraph + "\n\n" + secondParagraph);
    }

    @Test
    void rejectsATinyArticleCandidate() {
        serve(200, """
                <html><article>
                  <p>Next Upcoming</p>
                  <p>By Canary Media</p>
                </article></html>
                """);

        assertThatThrownBy(() -> fetcher.fetchContent(article(url("/article"))))
                .isInstanceOf(ArticleContentFetchException.class)
                .hasMessage("No usable article content was found");
    }

    @Test
    void reportsWhenAllSemanticCandidatesAreTooShort() {
        serve(200, """
                <html><body>
                  <article><p>Next Upcoming</p><p>By Canary Media</p></article>
                  <main><p>Subscribe</p><p>Newsletter signup</p></main>
                </body></html>
                """);

        assertThatThrownBy(() -> fetcher.fetchContent(article(url("/article"))))
                .isInstanceOf(ArticleContentFetchException.class)
                .hasMessage("No usable article content was found");
    }

    @Test
    void reportsNonSuccessfulResponses() {
        serve(503, "Temporarily unavailable");

        assertThatThrownBy(() -> fetcher.fetchContent(article(url("/article"))))
                .isInstanceOf(ArticleContentFetchException.class)
                .hasMessage("Article content request failed with HTTP 503");
    }

    @Test
    void reportsPagesWithoutUsableArticleBody() {
        serve(200, "<html><body><nav><p>Navigation only</p></nav></body></html>");

        assertThatThrownBy(() -> fetcher.fetchContent(article(url("/article"))))
                .isInstanceOf(ArticleContentFetchException.class)
                .hasMessage("No usable article content was found");
    }

    @Test
    void toleratesMalformedHtml() {
        String firstParagraph = "The malformed page still contains a detailed opening paragraph "
                + "about a utility-scale battery project and its expected operating schedule.";
        String secondParagraph = "A second substantial paragraph explains how the installation "
                + "will balance renewable generation and improve local grid reliability.";
        serve(200, "<html><article><p>" + firstParagraph + "<p>"
                + secondParagraph);

        assertThat(fetcher.fetchContent(article(url("/article"))))
                .isEqualTo(firstParagraph + "\n\n" + secondParagraph);
    }

    @Test
    void reportsMalformedUrls() {
        assertThatThrownBy(() -> fetcher.fetchContent(article("not a valid URL")))
                .isInstanceOf(ArticleContentFetchException.class)
                .hasMessage("Article URL is invalid");
    }

    @Test
    void reportsConnectionFailures() {
        String unavailableUrl = url("/article");
        server.stop(0);
        server = null;

        assertThatThrownBy(() -> fetcher.fetchContent(article(unavailableUrl)))
                .isInstanceOf(ArticleContentFetchException.class)
                .hasMessageStartingWith("Unable to fetch article content from ");
    }

    private void serve(int status, String response) {
        server.createContext("/article", exchange -> {
            byte[] body = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            exchange.sendResponseHeaders(status, body.length);
            try (var output = exchange.getResponseBody()) {
                output.write(body);
            }
        });
    }

    private String url(String path) {
        return "http://127.0.0.1:" + server.getAddress().getPort() + path;
    }

    private String fixture(String name) throws IOException {
        String resourcePath = "/fixtures/" + name;
        try (InputStream input = getClass().getResourceAsStream(resourcePath)) {
            if (input == null) {
                throw new IOException("Missing test fixture " + resourcePath);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private Article article(String url) {
        Source source = new Source(
                "Test source",
                "https://example.com/feed",
                SourceType.RSS,
                SourcePriority.MEDIUM
        );
        return new Article("Test article", url, source, Instant.parse("2026-08-20T00:00:00Z"));
    }
}
