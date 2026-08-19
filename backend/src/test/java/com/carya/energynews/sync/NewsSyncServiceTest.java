package com.carya.energynews.sync;

import com.carya.energynews.article.ArticleIngestionResult;
import com.carya.energynews.article.ArticleIngestionService;
import com.carya.energynews.collection.CollectedArticle;
import com.carya.energynews.collection.NewsCollectionException;
import com.carya.energynews.collection.NewsCollectionService;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NewsSyncServiceTest {

    @Mock
    private SourceRepository sourceRepository;

    @Mock
    private NewsCollectionService newsCollectionService;

    @Mock
    private ArticleIngestionService articleIngestionService;

    @InjectMocks
    private NewsSyncService newsSyncService;

    @Test
    void syncsOneSourceAndCountsSavedArticles() {
        Source source = source("RSS source", SourceType.RSS);
        List<CollectedArticle> articles = List.of(
                article("First article"),
                article("Second article")
        );
        when(newsCollectionService.collect(source)).thenReturn(articles);
        when(articleIngestionService.ingestAll(articles)).thenReturn(List.of(
                ArticleIngestionResult.SAVED,
                ArticleIngestionResult.SAVED
        ));

        NewsSyncResult result = newsSyncService.sync(source);

        assertThat(result).isEqualTo(new NewsSyncResult(2, 2, 0, 0));
        verify(newsCollectionService).collect(source);
        verify(articleIngestionService).ingestAll(articles);
    }

    @Test
    void countsDuplicateArticles() {
        Source source = source("RSS source", SourceType.RSS);
        List<CollectedArticle> articles = List.of(
                article("New article"),
                article("First duplicate"),
                article("Second duplicate")
        );
        when(newsCollectionService.collect(source)).thenReturn(articles);
        when(articleIngestionService.ingestAll(articles)).thenReturn(List.of(
                ArticleIngestionResult.SAVED,
                ArticleIngestionResult.DUPLICATE,
                ArticleIngestionResult.DUPLICATE
        ));

        NewsSyncResult result = newsSyncService.sync(source);

        assertThat(result).isEqualTo(new NewsSyncResult(3, 1, 2, 0));
    }

    @Test
    void processesMultipleEnabledRssSourcesAndAggregatesCounts() {
        Source firstSource = source("First RSS source", SourceType.RSS);
        Source secondSource = source("Second RSS source", SourceType.RSS);
        List<CollectedArticle> firstArticles = List.of(
                article("First saved article"),
                article("Duplicate article")
        );
        List<CollectedArticle> secondArticles = List.of(article("Second saved article"));
        when(sourceRepository.findAllByEnabledTrue()).thenReturn(List.of(firstSource, secondSource));
        when(newsCollectionService.collect(firstSource)).thenReturn(firstArticles);
        when(newsCollectionService.collect(secondSource)).thenReturn(secondArticles);
        when(articleIngestionService.ingestAll(firstArticles)).thenReturn(List.of(
                ArticleIngestionResult.SAVED,
                ArticleIngestionResult.DUPLICATE
        ));
        when(articleIngestionService.ingestAll(secondArticles)).thenReturn(List.of(
                ArticleIngestionResult.SAVED
        ));

        NewsSyncResult result = newsSyncService.syncAllEnabledSources();

        assertThat(result).isEqualTo(new NewsSyncResult(3, 2, 1, 0));
        verify(newsCollectionService).collect(firstSource);
        verify(newsCollectionService).collect(secondSource);
    }

    @Test
    void continuesAfterOneSourceFailsCollection() {
        Source failingSource = source("Failing RSS source", SourceType.RSS);
        Source healthySource = source("Healthy RSS source", SourceType.RSS);
        List<CollectedArticle> healthyArticles = List.of(article("Healthy article"));
        when(sourceRepository.findAllByEnabledTrue()).thenReturn(List.of(failingSource, healthySource));
        when(newsCollectionService.collect(failingSource))
                .thenThrow(new NewsCollectionException("Unable to fetch RSS feed"));
        when(newsCollectionService.collect(healthySource)).thenReturn(healthyArticles);
        when(articleIngestionService.ingestAll(healthyArticles)).thenReturn(List.of(
                ArticleIngestionResult.SAVED
        ));

        NewsSyncResult result = newsSyncService.syncAllEnabledSources();

        assertThat(result).isEqualTo(new NewsSyncResult(1, 1, 0, 1));
        verify(newsCollectionService).collect(failingSource);
        verify(newsCollectionService).collect(healthySource);
        verify(articleIngestionService).ingestAll(healthyArticles);
    }

    @Test
    void returnsZeroCountsForEmptyFeed() {
        Source source = source("Empty RSS source", SourceType.RSS);
        when(newsCollectionService.collect(source)).thenReturn(List.of());
        when(articleIngestionService.ingestAll(List.of())).thenReturn(List.of());

        NewsSyncResult result = newsSyncService.sync(source);

        assertThat(result).isEqualTo(new NewsSyncResult(0, 0, 0, 0));
        verify(articleIngestionService).ingestAll(List.of());
    }

    @Test
    void skipsUnsupportedSourcesDuringAllSourceSync() {
        Source rssSource = source("RSS source", SourceType.RSS);
        Source apiSource = source("API source", SourceType.API);
        Source websiteSource = source("Website source", SourceType.WEBSITE);
        when(sourceRepository.findAllByEnabledTrue()).thenReturn(List.of(
                rssSource,
                apiSource,
                websiteSource
        ));
        when(newsCollectionService.collect(rssSource)).thenReturn(List.of());
        when(articleIngestionService.ingestAll(List.of())).thenReturn(List.of());

        NewsSyncResult result = newsSyncService.syncAllEnabledSources();

        assertThat(result).isEqualTo(new NewsSyncResult(0, 0, 0, 0));
        verify(newsCollectionService).collect(rssSource);
        verify(newsCollectionService, never()).collect(apiSource);
        verify(newsCollectionService, never()).collect(websiteSource);
    }

    private Source source(String name, SourceType type) {
        return new Source(
                name,
                "https://example.com/" + name.replace(' ', '-').toLowerCase(),
                type,
                SourcePriority.MEDIUM
        );
    }

    private CollectedArticle article(String title) {
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
