package com.carya.energynews.watchlistdiscovery;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DiscoveryUrlNormalizerTest {

    private final DiscoveryUrlNormalizer normalizer = new DiscoveryUrlNormalizer();

    @Test
    void trimsAndRemovesFragmentWithoutChangingPublisherQuery() {
        assertThat(normalizer.normalize(
                "  https://news.example.com/article?id=7#comments  "
        )).contains("https://news.example.com/article?id=7");
    }

    @Test
    void rejectsUnsupportedOrMalformedUrls() {
        assertThat(normalizer.normalize("ftp://example.com/article")).isEmpty();
        assertThat(normalizer.normalize("not a url")).isEmpty();
        assertThat(normalizer.normalize("https://user@example.com/article")).isEmpty();
    }

    @Test
    void derivesStableOriginAndHostnameKey() {
        assertThat(normalizer.origin("https://WWW.Example.com:443/path?item=1"))
                .isEqualTo("https://www.example.com");
        assertThat(normalizer.hostKey("https://www.example.com/feed"))
                .isEqualTo("example.com");
        assertThat(normalizer.hostKey("https://example.com/article"))
                .isEqualTo("example.com");
    }
}
