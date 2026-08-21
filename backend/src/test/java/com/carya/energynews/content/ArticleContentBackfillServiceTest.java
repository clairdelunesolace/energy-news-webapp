package com.carya.energynews.content;

import com.carya.energynews.article.Article;
import com.carya.energynews.article.ArticleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ArticleContentBackfillServiceTest {

    @Mock
    private ArticleRepository articleRepository;

    @Mock
    private ArticleContentService articleContentService;

    @Test
    void isolatesFailuresAndContinuesWithRemainingCandidates() {
        Article first = org.mockito.Mockito.mock(Article.class);
        Article failing = org.mockito.Mockito.mock(Article.class);
        Article last = org.mockito.Mockito.mock(Article.class);
        when(articleRepository.findContentBackfillCandidates(any(Pageable.class)))
                .thenReturn(List.of(first, failing, last));
        when(articleContentService.enrichContent(first)).thenReturn(first);
        when(articleContentService.enrichContent(failing))
                .thenThrow(new ArticleContentFetchException("Fetch failed"));
        when(articleContentService.enrichContent(last)).thenReturn(last);

        ArticleContentBackfillResult result = service().backfill(3);

        assertThat(result).isEqualTo(new ArticleContentBackfillResult(3, 2, 1));
        InOrder calls = inOrder(articleContentService);
        calls.verify(articleContentService).enrichContent(first);
        calls.verify(articleContentService).enrichContent(failing);
        calls.verify(articleContentService).enrichContent(last);
        verify(articleRepository).findContentBackfillCandidates(
                org.mockito.ArgumentMatchers.argThat(pageable ->
                        pageable.getPageNumber() == 0 && pageable.getPageSize() == 3)
        );
    }

    @Test
    void returnsZeroCountsWhenNoCandidatesExist() {
        when(articleRepository.findContentBackfillCandidates(any(Pageable.class)))
                .thenReturn(List.of());

        assertThat(service().backfill(5))
                .isEqualTo(new ArticleContentBackfillResult(0, 0, 0));
        verifyNoInteractions(articleContentService);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 21})
    void rejectsLimitsOutsideTheAllowedRange(int limit) {
        assertThatThrownBy(() -> service().backfill(limit))
                .isInstanceOf(InvalidArticleContentBackfillLimitException.class)
                .hasMessage("Article content backfill limit must be between 1 and 20");

        verifyNoInteractions(articleRepository);
        verify(articleContentService, never()).enrichContent(any());
    }

    private ArticleContentBackfillService service() {
        return new ArticleContentBackfillService(articleRepository, articleContentService);
    }
}
