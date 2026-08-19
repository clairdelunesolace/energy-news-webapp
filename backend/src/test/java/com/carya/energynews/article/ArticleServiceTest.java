package com.carya.energynews.article;

import com.carya.energynews.source.Source;
import com.carya.energynews.source.SourceNotFoundException;
import com.carya.energynews.source.SourceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ArticleServiceTest {

    @Mock
    private ArticleRepository articleRepository;

    @Mock
    private SourceRepository sourceRepository;

    @InjectMocks
    private ArticleService articleService;

    @Test
    void returnsAllArticlesAsDtosWithoutCompleteSource() {
        Source source = source(7L, "Energy Storage News");
        Article article = new Article(
                "Stored article",
                "https://example.com/articles/stored",
                source,
                Instant.parse("2026-08-19T06:00:00Z")
        );
        article.onCreate();
        when(articleRepository.findAll()).thenReturn(List.of(article));

        List<ArticleResponse> responses = articleService.getAll();

        assertThat(responses).containsExactly(new ArticleResponse(
                null,
                "Stored article",
                "https://example.com/articles/stored",
                null,
                null,
                null,
                Instant.parse("2026-08-19T06:00:00Z"),
                7L,
                "Energy Storage News",
                article.getCreatedAt(),
                article.getUpdatedAt()
        ));
    }

    @Test
    void throwsWhenArticleDoesNotExist() {
        when(articleRepository.findById(42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> articleService.getById(42L))
                .isInstanceOf(ArticleNotFoundException.class)
                .hasMessage("Article with id 42 was not found");
    }

    @Test
    void throwsWhenRequestedSourceDoesNotExist() {
        CreateArticleRequest request = request();
        when(sourceRepository.findById(request.sourceId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> articleService.create(request))
                .isInstanceOf(SourceNotFoundException.class)
                .hasMessage("Source with id 7 was not found");
        verify(articleRepository, never()).saveAndFlush(any(Article.class));
    }

    @Test
    void createsArticleWithBackendCollectionTimeAndResolvedSource() {
        CreateArticleRequest request = request();
        Source source = source(7L, "Energy Storage News");
        when(sourceRepository.findById(request.sourceId())).thenReturn(Optional.of(source));
        when(articleRepository.existsByUrl(request.url())).thenReturn(false);
        when(articleRepository.saveAndFlush(any(Article.class))).thenAnswer(invocation -> {
            Article article = invocation.getArgument(0);
            article.onCreate();
            return article;
        });
        Instant beforeCreate = Instant.now();

        ArticleResponse response = articleService.create(request);

        Instant afterCreate = Instant.now();
        ArgumentCaptor<Article> articleCaptor = ArgumentCaptor.forClass(Article.class);
        verify(articleRepository).saveAndFlush(articleCaptor.capture());
        Article saved = articleCaptor.getValue();
        assertThat(saved.getSource()).isSameAs(source);
        assertThat(saved.getCollectedAt()).isBetween(beforeCreate, afterCreate);
        assertThat(response.sourceId()).isEqualTo(7L);
        assertThat(response.sourceName()).isEqualTo("Energy Storage News");
        assertThat(response.collectedAt()).isEqualTo(saved.getCollectedAt());
        assertThat(response.description()).isEqualTo(request.description());
        assertThat(response.content()).isEqualTo(request.content());
        assertThat(response.publishedAt()).isEqualTo(request.publishedAt());
    }

    @Test
    void rejectsKnownDuplicateUrlBeforeSaving() {
        CreateArticleRequest request = request();
        when(sourceRepository.findById(request.sourceId()))
                .thenReturn(Optional.of(mock(Source.class)));
        when(articleRepository.existsByUrl(request.url())).thenReturn(true);

        assertThatThrownBy(() -> articleService.create(request))
                .isInstanceOf(DuplicateArticleUrlException.class)
                .hasMessage("An article with URL 'https://example.com/articles/new' already exists");
        verify(articleRepository, never()).saveAndFlush(any(Article.class));
    }

    @Test
    void translatesDatabaseConstraintViolationIntoDuplicateUrlError() {
        CreateArticleRequest request = request();
        when(sourceRepository.findById(request.sourceId()))
                .thenReturn(Optional.of(mock(Source.class)));
        when(articleRepository.existsByUrl(request.url())).thenReturn(false);
        DataIntegrityViolationException databaseException =
                new DataIntegrityViolationException("unique constraint");
        when(articleRepository.saveAndFlush(any(Article.class))).thenThrow(databaseException);

        assertThatThrownBy(() -> articleService.create(request))
                .isInstanceOf(DuplicateArticleUrlException.class)
                .hasMessage("An article with URL 'https://example.com/articles/new' already exists")
                .hasCause(databaseException);
    }

    private static CreateArticleRequest request() {
        return new CreateArticleRequest(
                "New article",
                "https://example.com/articles/new",
                "Article summary",
                "Article content",
                Instant.parse("2026-08-18T12:00:00Z"),
                7L
        );
    }

    private static Source source(Long id, String name) {
        Source source = mock(Source.class);
        when(source.getId()).thenReturn(id);
        when(source.getName()).thenReturn(name);
        return source;
    }
}
