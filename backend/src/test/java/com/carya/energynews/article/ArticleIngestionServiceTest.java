package com.carya.energynews.article;

import com.carya.energynews.collection.CollectedArticle;
import com.carya.energynews.source.Source;
import com.carya.energynews.source.SourceNotFoundException;
import com.carya.energynews.source.SourcePriority;
import com.carya.energynews.source.SourceRepository;
import com.carya.energynews.source.SourceType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ArticleIngestionServiceTest {

    @Mock
    private ArticleRepository articleRepository;

    @Mock
    private SourceRepository sourceRepository;

    @InjectMocks
    private ArticleIngestionService articleIngestionService;

    @Test
    void persistsNewCollectedArticleWithResolvedSource() {
        Source source = source();
        CollectedArticle collectedArticle = collectedArticle(
                "New article",
                "https://example.com/articles/new",
                Instant.parse("2026-08-18T12:00:00Z")
        );
        when(sourceRepository.findById(7L)).thenReturn(Optional.of(source));
        when(articleRepository.existsByUrl(collectedArticle.url())).thenReturn(false);
        Instant beforeIngestion = Instant.now();

        ArticleIngestionResult result = articleIngestionService.ingest(collectedArticle);

        Instant afterIngestion = Instant.now();
        ArgumentCaptor<Article> captor = ArgumentCaptor.forClass(Article.class);
        verify(articleRepository).save(captor.capture());
        Article saved = captor.getValue();
        assertThat(result).isEqualTo(ArticleIngestionResult.SAVED);
        assertThat(saved.getTitle()).isEqualTo(collectedArticle.title());
        assertThat(saved.getUrl()).isEqualTo(collectedArticle.url());
        assertThat(saved.getDescription()).isEqualTo(collectedArticle.description());
        assertThat(saved.getContent()).isEqualTo(collectedArticle.content());
        assertThat(saved.getPublishedAt()).isEqualTo(collectedArticle.publishedAt());
        assertThat(saved.getCollectedAt()).isBetween(beforeIngestion, afterIngestion);
        assertThat(saved.getSource()).isSameAs(source);
        assertThat(saved.getId()).isNull();
        assertThat(saved.getCreatedAt()).isNull();
        assertThat(saved.getUpdatedAt()).isNull();
    }

    @Test
    void skipsDuplicateUrl() {
        CollectedArticle collectedArticle = collectedArticle(
                "Duplicate article",
                "https://example.com/articles/duplicate",
                Instant.parse("2026-08-18T12:00:00Z")
        );
        when(sourceRepository.findById(7L)).thenReturn(Optional.of(source()));
        when(articleRepository.existsByUrl(collectedArticle.url())).thenReturn(true);

        ArticleIngestionResult result = articleIngestionService.ingest(collectedArticle);

        assertThat(result).isEqualTo(ArticleIngestionResult.DUPLICATE);
        verify(articleRepository, never()).save(any(Article.class));
    }

    @Test
    void failsClearlyWhenSourceDoesNotExist() {
        CollectedArticle collectedArticle = collectedArticle(
                "Missing source article",
                "https://example.com/articles/missing-source",
                null
        );
        when(sourceRepository.findById(7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> articleIngestionService.ingest(collectedArticle))
                .isInstanceOf(SourceNotFoundException.class)
                .hasMessage("Source with id 7 was not found");
        verifyNoInteractions(articleRepository);
    }

    @Test
    void persistsArticleWithNullPublishedAt() {
        Source source = source();
        CollectedArticle collectedArticle = collectedArticle(
                "Undated article",
                "https://example.com/articles/undated",
                null
        );
        when(sourceRepository.findById(7L)).thenReturn(Optional.of(source));
        when(articleRepository.existsByUrl(collectedArticle.url())).thenReturn(false);

        ArticleIngestionResult result = articleIngestionService.ingest(collectedArticle);

        ArgumentCaptor<Article> captor = ArgumentCaptor.forClass(Article.class);
        verify(articleRepository).save(captor.capture());
        assertThat(result).isEqualTo(ArticleIngestionResult.SAVED);
        assertThat(captor.getValue().getPublishedAt()).isNull();
    }

    @Test
    void batchIngestionSavesMultipleNewArticles() {
        CollectedArticle first = collectedArticle(
                "First article",
                "https://example.com/articles/first",
                null
        );
        CollectedArticle second = collectedArticle(
                "Second article",
                "https://example.com/articles/second",
                null
        );
        when(sourceRepository.findById(7L)).thenReturn(Optional.of(source()));
        when(articleRepository.existsByUrl(anyString())).thenReturn(false);

        List<ArticleIngestionResult> results = articleIngestionService.ingestAll(List.of(first, second));

        assertThat(results).containsExactly(
                ArticleIngestionResult.SAVED,
                ArticleIngestionResult.SAVED
        );
        verify(articleRepository, times(2)).save(any(Article.class));
    }

    @Test
    void batchIngestionContinuesPastDuplicates() {
        CollectedArticle duplicate = collectedArticle(
                "Duplicate article",
                "https://example.com/articles/duplicate",
                null
        );
        CollectedArticle newArticle = collectedArticle(
                "New article",
                "https://example.com/articles/new-in-batch",
                null
        );
        when(sourceRepository.findById(7L)).thenReturn(Optional.of(source()));
        when(articleRepository.existsByUrl(duplicate.url())).thenReturn(true);
        when(articleRepository.existsByUrl(newArticle.url())).thenReturn(false);

        List<ArticleIngestionResult> results = articleIngestionService.ingestAll(
                List.of(duplicate, newArticle)
        );

        assertThat(results).containsExactly(
                ArticleIngestionResult.DUPLICATE,
                ArticleIngestionResult.SAVED
        );
        ArgumentCaptor<Article> captor = ArgumentCaptor.forClass(Article.class);
        verify(articleRepository).save(captor.capture());
        assertThat(captor.getValue().getUrl()).isEqualTo(newArticle.url());
    }

    private Source source() {
        return new Source(
                "Energy Storage News",
                "https://example.com/feed",
                SourceType.RSS,
                SourcePriority.HIGH
        );
    }

    private CollectedArticle collectedArticle(String title, String url, Instant publishedAt) {
        return new CollectedArticle(
                title,
                url,
                "Article summary",
                "Article content",
                publishedAt,
                7L
        );
    }
}
