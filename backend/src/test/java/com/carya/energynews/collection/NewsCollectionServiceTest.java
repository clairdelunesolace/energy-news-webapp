package com.carya.energynews.collection;

import com.carya.energynews.source.Source;
import com.carya.energynews.source.SourcePriority;
import com.carya.energynews.source.SourceRepository;
import com.carya.energynews.source.SourceType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NewsCollectionServiceTest {

    @Mock
    private SourceRepository sourceRepository;

    @Mock
    private RssCollector rssCollector;

    @InjectMocks
    private NewsCollectionService newsCollectionService;

    @Test
    void collectsOneRssSource() {
        Source source = source("RSS source", SourceType.RSS);
        CollectedArticle article = collectedArticle("One article");
        when(rssCollector.collect(source)).thenReturn(List.of(article));

        List<CollectedArticle> result = newsCollectionService.collect(source);

        assertThat(result).containsExactly(article);
        verify(rssCollector).collect(source);
    }

    @Test
    void collectsAndCombinesAllEnabledRssSources() {
        Source firstSource = source("First RSS source", SourceType.RSS);
        Source secondSource = source("Second RSS source", SourceType.RSS);
        CollectedArticle firstArticle = collectedArticle("First article");
        CollectedArticle secondArticle = collectedArticle("Second article");
        when(sourceRepository.findAllByEnabledTrue()).thenReturn(List.of(firstSource, secondSource));
        when(rssCollector.collect(firstSource)).thenReturn(List.of(firstArticle));
        when(rssCollector.collect(secondSource)).thenReturn(List.of(secondArticle));

        List<CollectedArticle> result = newsCollectionService.collectAllEnabledSources();

        assertThat(result).containsExactly(firstArticle, secondArticle);
        verify(rssCollector).collect(firstSource);
        verify(rssCollector).collect(secondSource);
    }

    @Test
    void usesEnabledSourceQuerySoDisabledSourcesAreSkipped() {
        when(sourceRepository.findAllByEnabledTrue()).thenReturn(List.of());

        List<CollectedArticle> result = newsCollectionService.collectAllEnabledSources();

        assertThat(result).isEmpty();
        verify(sourceRepository).findAllByEnabledTrue();
        verify(sourceRepository, never()).findAll();
        verifyNoInteractions(rssCollector);
    }

    @Test
    void rejectsUnsupportedSourceTypeWhenCollectingDirectly() {
        Source source = source("API source", SourceType.API);

        assertThatThrownBy(() -> newsCollectionService.collect(source))
                .isInstanceOf(NewsCollectionException.class)
                .hasMessage("Unsupported source type for news collection: API");
        verifyNoInteractions(rssCollector);
    }

    @Test
    void skipsUnsupportedEnabledSourcesWhenCollectingAll() {
        Source rssSource = source("RSS source", SourceType.RSS);
        Source websiteSource = source("Website source", SourceType.WEBSITE);
        CollectedArticle article = collectedArticle("RSS article");
        when(sourceRepository.findAllByEnabledTrue()).thenReturn(List.of(rssSource, websiteSource));
        when(rssCollector.collect(rssSource)).thenReturn(List.of(article));

        List<CollectedArticle> result = newsCollectionService.collectAllEnabledSources();

        assertThat(result).containsExactly(article);
        verify(rssCollector).collect(rssSource);
        verify(rssCollector, never()).collect(websiteSource);
    }

    @Test
    void propagatesCollectorFailures() {
        Source source = source("Failing RSS source", SourceType.RSS);
        NewsCollectionException failure = new NewsCollectionException("Unable to fetch RSS feed");
        when(rssCollector.collect(source)).thenThrow(failure);

        assertThatThrownBy(() -> newsCollectionService.collect(source))
                .isSameAs(failure);
    }

    private Source source(String name, SourceType type) {
        return new Source(
                name,
                "https://example.com/" + name.replace(' ', '-').toLowerCase(),
                type,
                SourcePriority.MEDIUM
        );
    }

    private CollectedArticle collectedArticle(String title) {
        return new CollectedArticle(
                title,
                "https://example.com/articles/" + title.replace(' ', '-').toLowerCase(),
                null,
                null,
                null,
                null
        );
    }
}
