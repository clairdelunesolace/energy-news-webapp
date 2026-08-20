package com.carya.energynews.sync;

import com.carya.energynews.article.ArticleIngestionResult;
import com.carya.energynews.article.ArticleIngestionService;
import com.carya.energynews.collection.CollectedArticle;
import com.carya.energynews.collection.NewsCollectionException;
import com.carya.energynews.collection.NewsCollectionService;
import com.carya.energynews.filter.ArticleFilter;
import com.carya.energynews.filter.FilterResult;
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
    private ArticleFilter articleFilter;

    @Mock
    private ArticleIngestionService articleIngestionService;

    @InjectMocks
    private NewsSyncService newsSyncService;

    @Test
    void acceptedArticleReachesIngestion() {
        Source source = source("RSS source", SourceType.RSS);
        CollectedArticle article = article("Accepted article");
        when(newsCollectionService.collect(source)).thenReturn(List.of(article));
        when(articleFilter.evaluate(article)).thenReturn(accepted());
        when(articleIngestionService.ingestAll(List.of(article))).thenReturn(List.of(
                ArticleIngestionResult.SAVED
        ));

        NewsSyncResult result = newsSyncService.sync(source);

        assertThat(result).isEqualTo(new NewsSyncResult(1, 0, 1, 0, 0));
        verify(articleFilter).evaluate(article);
        verify(articleIngestionService).ingestAll(List.of(article));
    }

    @Test
    void rejectedArticleDoesNotReachIngestion() {
        Source source = source("RSS source", SourceType.RSS);
        CollectedArticle article = article("Rejected article");
        when(newsCollectionService.collect(source)).thenReturn(List.of(article));
        when(articleFilter.evaluate(article)).thenReturn(rejected());
        when(articleIngestionService.ingestAll(List.of())).thenReturn(List.of());

        NewsSyncResult result = newsSyncService.sync(source);

        assertThat(result).isEqualTo(new NewsSyncResult(1, 1, 0, 0, 0));
        verify(articleFilter).evaluate(article);
        verify(articleIngestionService).ingestAll(List.of());
    }

    @Test
    void filtersMixedArticlesAndStillCountsDuplicates() {
        Source source = source("RSS source", SourceType.RSS);
        CollectedArticle savedArticle = article("Saved article");
        CollectedArticle rejectedArticle = article("Rejected article");
        CollectedArticle duplicateArticle = article("Duplicate article");
        List<CollectedArticle> collectedArticles = List.of(
                savedArticle,
                rejectedArticle,
                duplicateArticle
        );
        List<CollectedArticle> acceptedArticles = List.of(savedArticle, duplicateArticle);
        when(newsCollectionService.collect(source)).thenReturn(collectedArticles);
        when(articleFilter.evaluate(savedArticle)).thenReturn(accepted());
        when(articleFilter.evaluate(rejectedArticle)).thenReturn(rejected());
        when(articleFilter.evaluate(duplicateArticle)).thenReturn(accepted());
        when(articleIngestionService.ingestAll(acceptedArticles)).thenReturn(List.of(
                ArticleIngestionResult.SAVED,
                ArticleIngestionResult.DUPLICATE
        ));

        NewsSyncResult result = newsSyncService.sync(source);

        assertThat(result).isEqualTo(new NewsSyncResult(3, 1, 1, 1, 0));
        verify(articleIngestionService).ingestAll(acceptedArticles);
    }

    @Test
    void processesMultipleEnabledRssSourcesAndAggregatesCounts() {
        Source firstSource = source("First RSS source", SourceType.RSS);
        Source secondSource = source("Second RSS source", SourceType.RSS);
        CollectedArticle firstAcceptedArticle = article("First accepted article");
        CollectedArticle filteredArticle = article("Filtered article");
        CollectedArticle secondAcceptedArticle = article("Second accepted article");
        List<CollectedArticle> firstArticles = List.of(firstAcceptedArticle, filteredArticle);
        List<CollectedArticle> secondArticles = List.of(secondAcceptedArticle);
        when(sourceRepository.findAllByEnabledTrue()).thenReturn(List.of(firstSource, secondSource));
        when(newsCollectionService.collect(firstSource)).thenReturn(firstArticles);
        when(newsCollectionService.collect(secondSource)).thenReturn(secondArticles);
        when(articleFilter.evaluate(firstAcceptedArticle)).thenReturn(accepted());
        when(articleFilter.evaluate(filteredArticle)).thenReturn(rejected());
        when(articleFilter.evaluate(secondAcceptedArticle)).thenReturn(accepted());
        when(articleIngestionService.ingestAll(List.of(firstAcceptedArticle))).thenReturn(List.of(
                ArticleIngestionResult.SAVED
        ));
        when(articleIngestionService.ingestAll(List.of(secondAcceptedArticle))).thenReturn(List.of(
                ArticleIngestionResult.SAVED
        ));

        NewsSyncResult result = newsSyncService.syncAllEnabledSources();

        assertThat(result).isEqualTo(new NewsSyncResult(3, 1, 2, 0, 0));
        verify(newsCollectionService).collect(firstSource);
        verify(newsCollectionService).collect(secondSource);
    }

    @Test
    void continuesAfterOneSourceFailsCollection() {
        Source failingSource = source("Failing RSS source", SourceType.RSS);
        Source healthySource = source("Healthy RSS source", SourceType.RSS);
        CollectedArticle healthyArticle = article("Healthy article");
        when(sourceRepository.findAllByEnabledTrue()).thenReturn(List.of(failingSource, healthySource));
        when(newsCollectionService.collect(failingSource))
                .thenThrow(new NewsCollectionException("Unable to fetch RSS feed"));
        when(newsCollectionService.collect(healthySource)).thenReturn(List.of(healthyArticle));
        when(articleFilter.evaluate(healthyArticle)).thenReturn(accepted());
        when(articleIngestionService.ingestAll(List.of(healthyArticle))).thenReturn(List.of(
                ArticleIngestionResult.SAVED
        ));

        NewsSyncResult result = newsSyncService.syncAllEnabledSources();

        assertThat(result).isEqualTo(new NewsSyncResult(1, 0, 1, 0, 1));
        verify(newsCollectionService).collect(failingSource);
        verify(newsCollectionService).collect(healthySource);
        verify(articleIngestionService).ingestAll(List.of(healthyArticle));
    }

    @Test
    void returnsZeroCountsForEmptyFeed() {
        Source source = source("Empty RSS source", SourceType.RSS);
        when(newsCollectionService.collect(source)).thenReturn(List.of());
        when(articleIngestionService.ingestAll(List.of())).thenReturn(List.of());

        NewsSyncResult result = newsSyncService.sync(source);

        assertThat(result).isEqualTo(new NewsSyncResult(0, 0, 0, 0, 0));
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

        assertThat(result).isEqualTo(new NewsSyncResult(0, 0, 0, 0, 0));
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

    private FilterResult accepted() {
        return new FilterResult(true, "Matched keyword");
    }

    private FilterResult rejected() {
        return new FilterResult(false, "No configured keyword matched");
    }
}
