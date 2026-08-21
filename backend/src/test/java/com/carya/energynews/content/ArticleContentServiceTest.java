package com.carya.energynews.content;

import com.carya.energynews.article.Article;
import com.carya.energynews.article.ArticleRepository;
import com.carya.energynews.source.Source;
import com.carya.energynews.source.SourcePriority;
import com.carya.energynews.source.SourceType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ArticleContentServiceTest {

    @Mock
    private ArticleContentFetcher articleContentFetcher;

    @Mock
    private ArticleRepository articleRepository;

    @Test
    void skipsArticlesThatAlreadyHaveNonBlankContent() {
        Article article = article("existing");
        article.setContent("Existing article content");
        ArticleContentService service = service();

        Article result = service.enrichContent(article);

        assertThat(result).isSameAs(article);
        assertThat(result.getContent()).isEqualTo("Existing article content");
        verifyNoInteractions(articleContentFetcher, articleRepository);
    }

    @Test
    void fetchesAndPersistsMissingContent() {
        Article article = article("missing");
        when(articleContentFetcher.fetchContent(article))
                .thenReturn("First paragraph.\n\nSecond paragraph.");
        when(articleRepository.saveAndFlush(any(Article.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Article result = service().enrichContent(article);

        assertThat(result.getContent()).isEqualTo("First paragraph.\n\nSecond paragraph.");
        verify(articleContentFetcher).fetchContent(article);
        verify(articleRepository).saveAndFlush(article);
    }

    @Test
    void doesNotPersistAnEmptyFetcherResult() {
        Article article = article("empty");
        when(articleContentFetcher.fetchContent(article)).thenReturn("   ");

        assertThatThrownBy(() -> service().enrichContent(article))
                .isInstanceOf(ArticleContentFetchException.class)
                .hasMessage("Content fetcher returned no usable article content");

        verify(articleRepository, never()).saveAndFlush(any());
    }

    private ArticleContentService service() {
        return new ArticleContentService(articleContentFetcher, articleRepository);
    }

    private Article article(String suffix) {
        Source source = new Source(
                "Test source",
                "https://example.com/feed/" + suffix,
                SourceType.RSS,
                SourcePriority.MEDIUM
        );
        return new Article(
                "Test article",
                "https://example.com/articles/" + suffix,
                source,
                Instant.parse("2026-08-20T00:00:00Z")
        );
    }
}
