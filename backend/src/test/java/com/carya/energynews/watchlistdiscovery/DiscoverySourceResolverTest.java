package com.carya.energynews.watchlistdiscovery;

import com.carya.energynews.discovery.DiscoveredArticle;
import com.carya.energynews.source.Source;
import com.carya.energynews.source.SourceLanguage;
import com.carya.energynews.source.SourcePriority;
import com.carya.energynews.source.SourceRepository;
import com.carya.energynews.source.SourceType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DiscoverySourceResolverTest {

    @Mock
    private SourceRepository sourceRepository;

    @Test
    void reusesExistingSourceByHostnameWithoutChangingConfiguration() {
        Source existing = new Source(
                "Existing Publisher",
                "https://www.example.com/rss/feed",
                SourceType.RSS,
                SourcePriority.HIGH,
                SourceLanguage.EN,
                true
        );
        existing.setEnabled(true);
        when(sourceRepository.findAll()).thenReturn(List.of(existing));
        DiscoverySourceResolver resolver = resolver();

        Optional<Source> resolved = resolver.resolve(
                article("Different provider name", null),
                "https://example.com/news/article"
        );

        assertThat(resolved).containsSame(existing);
        assertThat(existing.getType()).isEqualTo(SourceType.RSS);
        assertThat(existing.isEnabled()).isTrue();
        assertThat(existing.isContentEnrichmentEnabled()).isTrue();
        verify(sourceRepository, never()).saveAndFlush(any(Source.class));
    }

    @Test
    void reusesExistingSourceByNormalizedName() {
        Source existing = new Source(
                "pv   magazine",
                "https://legacy.example/feed",
                SourceType.RSS,
                SourcePriority.HIGH,
                SourceLanguage.EN
        );
        when(sourceRepository.findAll()).thenReturn(List.of(existing));

        assertThat(resolver().resolve(
                article("  PV Magazine ", null),
                "https://new-domain.example/article"
        )).containsSame(existing);
        verify(sourceRepository, never()).saveAndFlush(any(Source.class));
    }

    @Test
    void createsDisabledWebsiteSourceForSupportedLanguage() {
        when(sourceRepository.findAll()).thenReturn(List.of());
        when(sourceRepository.saveAndFlush(any(Source.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Optional<Source> resolved = resolver().resolve(
                article("Publisher News", "zh-cn"),
                "https://news.example.cn/path/article?id=1"
        );

        ArgumentCaptor<Source> captor = ArgumentCaptor.forClass(Source.class);
        verify(sourceRepository).saveAndFlush(captor.capture());
        Source created = captor.getValue();
        assertThat(resolved).containsSame(created);
        assertThat(created.getName()).isEqualTo("Publisher News");
        assertThat(created.getUrl()).isEqualTo("https://news.example.cn");
        assertThat(created.getType()).isEqualTo(SourceType.WEBSITE);
        assertThat(created.getPriority()).isNull();
        assertThat(created.getLanguage()).isEqualTo(SourceLanguage.ZH_CN);
        assertThat(created.isEnabled()).isFalse();
        assertThat(created.isContentEnrichmentEnabled()).isFalse();
    }

    @Test
    void skipsNewSourceWhenLanguageIsMissingOrUnsupported() {
        when(sourceRepository.findAll()).thenReturn(List.of());

        assertThat(resolver().resolve(
                article(null, null),
                "https://example.com/article"
        )).isEmpty();
        assertThat(resolver().resolve(
                article("Example", "fr"),
                "https://example.com/another"
        )).isEmpty();
        verify(sourceRepository, never()).saveAndFlush(any(Source.class));
    }

    private DiscoverySourceResolver resolver() {
        return new DiscoverySourceResolver(sourceRepository, new DiscoveryUrlNormalizer());
    }

    private DiscoveredArticle article(String sourceName, String languageCode) {
        return new DiscoveredArticle(
                "Relevant article",
                "https://example.com/article",
                "Relevant description",
                sourceName,
                null,
                languageCode
        );
    }
}
