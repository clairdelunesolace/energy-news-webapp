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
import org.springframework.test.util.ReflectionTestUtils;

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
        when(articleRepository.findByUrl(collectedArticle.url())).thenReturn(Optional.empty());
        persistSavedArticles();
        Instant beforeIngestion = Instant.now();

        ArticleIngestionResult result = articleIngestionService.ingest(collectedArticle);

        Instant afterIngestion = Instant.now();
        ArgumentCaptor<Article> captor = ArgumentCaptor.forClass(Article.class);
        verify(articleRepository).saveAndFlush(captor.capture());
        Article saved = captor.getValue();
        assertThat(result.status()).isEqualTo(ArticleIngestionResult.Status.SAVED);
        assertThat(result.article()).isSameAs(saved);
        assertThat(saved.getTitle()).isEqualTo(collectedArticle.title());
        assertThat(saved.getUrl()).isEqualTo(collectedArticle.url());
        assertThat(saved.getDescription()).isEqualTo(collectedArticle.description());
        assertThat(saved.getContent()).isEqualTo(collectedArticle.content());
        assertThat(saved.getPublishedAt()).isEqualTo(collectedArticle.publishedAt());
        assertThat(saved.getCollectedAt()).isBetween(beforeIngestion, afterIngestion);
        assertThat(saved.getSource()).isSameAs(source);
        assertThat(saved.getId()).isEqualTo(13L);
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
        Source source = source();
        Article existing = persistedArticle("Existing article", collectedArticle.url(), source, 23L);
        when(sourceRepository.findById(7L)).thenReturn(Optional.of(source));
        when(articleRepository.findByUrl(collectedArticle.url())).thenReturn(Optional.of(existing));

        ArticleIngestionResult result = articleIngestionService.ingest(collectedArticle);

        assertThat(result.status()).isEqualTo(ArticleIngestionResult.Status.DUPLICATE);
        assertThat(result.article()).isSameAs(existing);
        verify(articleRepository, never()).saveAndFlush(any(Article.class));
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
        when(articleRepository.findByUrl(collectedArticle.url())).thenReturn(Optional.empty());
        persistSavedArticles();

        ArticleIngestionResult result = articleIngestionService.ingest(collectedArticle);

        ArgumentCaptor<Article> captor = ArgumentCaptor.forClass(Article.class);
        verify(articleRepository).saveAndFlush(captor.capture());
        assertThat(result.status()).isEqualTo(ArticleIngestionResult.Status.SAVED);
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
        when(articleRepository.findByUrl(anyString())).thenReturn(Optional.empty());
        persistSavedArticles();

        List<ArticleIngestionResult> results = articleIngestionService.ingestAll(List.of(first, second));

        assertThat(results).extracting(ArticleIngestionResult::status).containsExactly(
                ArticleIngestionResult.Status.SAVED,
                ArticleIngestionResult.Status.SAVED
        );
        verify(articleRepository, times(2)).saveAndFlush(any(Article.class));
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
        Source source = source();
        Article existing = persistedArticle("Existing duplicate", duplicate.url(), source, 23L);
        when(sourceRepository.findById(7L)).thenReturn(Optional.of(source));
        when(articleRepository.findByUrl(duplicate.url())).thenReturn(Optional.of(existing));
        when(articleRepository.findByUrl(newArticle.url())).thenReturn(Optional.empty());
        persistSavedArticles();

        List<ArticleIngestionResult> results = articleIngestionService.ingestAll(
                List.of(duplicate, newArticle)
        );

        assertThat(results).extracting(ArticleIngestionResult::status).containsExactly(
                ArticleIngestionResult.Status.DUPLICATE,
                ArticleIngestionResult.Status.SAVED
        );
        assertThat(results.getFirst().article()).isSameAs(existing);
        ArgumentCaptor<Article> captor = ArgumentCaptor.forClass(Article.class);
        verify(articleRepository).saveAndFlush(captor.capture());
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

    private Article persistedArticle(String title, String url, Source source, Long id) {
        Article article = new Article(title, url, source, Instant.parse("2026-08-20T01:00:00Z"));
        ReflectionTestUtils.setField(article, "id", id);
        return article;
    }

    private void persistSavedArticles() {
        when(articleRepository.saveAndFlush(any(Article.class))).thenAnswer(invocation -> {
            Article article = invocation.getArgument(0);
            ReflectionTestUtils.setField(article, "id", 13L);
            return article;
        });
    }
}
