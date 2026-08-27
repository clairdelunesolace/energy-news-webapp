package com.carya.energynews.discovery;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NewsDiscoveryProviderContractTest {

    @Test
    void fakeProviderReceivesNeutralQueryAndReturnsNeutralCandidates() {
        DiscoveredArticle candidate = new DiscoveredArticle(
                "New battery project",
                "https://example.com/new-battery-project",
                "A grid-scale project was announced.",
                "Example News",
                Instant.parse("2026-08-26T06:00:00Z")
        );
        CapturingProvider provider = new CapturingProvider(List.of(candidate));
        NewsDiscoveryQuery query = new NewsDiscoveryQuery(
                "battery energy storage",
                Instant.parse("2026-08-20T00:00:00Z"),
                Instant.parse("2026-08-27T00:00:00Z"),
                20
        );

        List<DiscoveredArticle> results = provider.discover(query);

        assertThat(provider.providerName()).isEqualTo("test-provider");
        assertThat(provider.receivedQuery).isSameAs(query);
        assertThat(results).containsExactly(candidate);
    }

    private static final class CapturingProvider implements NewsDiscoveryProvider {

        private final List<DiscoveredArticle> results;
        private NewsDiscoveryQuery receivedQuery;

        private CapturingProvider(List<DiscoveredArticle> results) {
            this.results = results;
        }

        @Override
        public String providerName() {
            return "test-provider";
        }

        @Override
        public List<DiscoveredArticle> discover(NewsDiscoveryQuery query) {
            receivedQuery = query;
            return results;
        }
    }
}
