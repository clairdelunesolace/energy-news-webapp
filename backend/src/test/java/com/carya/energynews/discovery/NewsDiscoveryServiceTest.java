package com.carya.energynews.discovery;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NewsDiscoveryServiceTest {

    @Mock
    private NewsDiscoveryProvider provider;

    private NewsDiscoveryService discoveryService;

    @BeforeEach
    void setUp() {
        discoveryService = new NewsDiscoveryService(provider);
    }

    @Test
    void delegatesExactQueryAndReturnsProviderResultsUnchanged() {
        NewsDiscoveryQuery query = query();
        List<DiscoveredArticle> providerResults = List.of(article());
        when(provider.discover(query)).thenReturn(providerResults);

        List<DiscoveredArticle> result = discoveryService.discover(query);

        assertThat(result).isSameAs(providerResults);
        verify(provider).discover(query);
        verifyNoMoreInteractions(provider);
    }

    @Test
    void delegatesProviderName() {
        when(provider.providerName()).thenReturn("brave-news");

        assertThat(discoveryService.providerName()).isEqualTo("brave-news");

        verify(provider).providerName();
        verifyNoMoreInteractions(provider);
    }

    @Test
    void propagatesProviderNeutralExceptionUnchanged() {
        NewsDiscoveryQuery query = query();
        NewsDiscoveryException failure = new NewsDiscoveryException("Provider unavailable");
        when(provider.discover(query)).thenThrow(failure);

        assertThatThrownBy(() -> discoveryService.discover(query))
                .isSameAs(failure);

        verify(provider).discover(query);
        verifyNoMoreInteractions(provider);
    }

    private static NewsDiscoveryQuery query() {
        return new NewsDiscoveryQuery(
                "800VDC",
                Instant.parse("2026-08-20T00:00:00Z"),
                Instant.parse("2026-08-27T00:00:00Z"),
                20
        );
    }

    private static DiscoveredArticle article() {
        return new DiscoveredArticle(
                "800VDC architecture expands",
                "https://example.com/800vdc",
                null,
                "Example News",
                Instant.parse("2026-08-26T08:00:00Z")
        );
    }
}
