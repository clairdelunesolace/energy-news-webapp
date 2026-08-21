package com.carya.energynews.translation;

import com.carya.energynews.article.Article;
import com.carya.energynews.source.SourceLanguage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ArticleContentTranslationBackfillServiceTest {

    @Mock
    private ArticleTranslationRepository articleTranslationRepository;

    @Mock
    private ArticleContentTranslationService articleContentTranslationService;

    @Test
    void isolatesFailuresAndContinuesWithRemainingCandidates() {
        Article firstArticle = org.mockito.Mockito.mock(Article.class);
        Article failingArticle = org.mockito.Mockito.mock(Article.class);
        Article lastArticle = org.mockito.Mockito.mock(Article.class);
        ArticleTranslation first = translation(firstArticle);
        ArticleTranslation failing = translation(failingArticle);
        ArticleTranslation last = translation(lastArticle);
        when(articleTranslationRepository.findContentTranslationBackfillCandidates(
                eq(SourceLanguage.EN),
                eq(TranslationLanguage.ZH_CN),
                eq(TranslationStatus.SUCCESS),
                eq(List.of(ContentTranslationStatus.PENDING, ContentTranslationStatus.FAILED)),
                any(Pageable.class)
        )).thenReturn(List.of(first, failing, last));
        when(articleContentTranslationService.translateContent(
                any(Article.class),
                eq(TranslationLanguage.ZH_CN)
        )).thenAnswer(invocation -> {
            Article article = invocation.getArgument(0);
            if (article == failingArticle) {
                throw new TranslationException("Provider unavailable");
            }
            return null;
        });

        ArticleContentTranslationBackfillResult result = service().backfill(3);

        assertThat(result).isEqualTo(new ArticleContentTranslationBackfillResult(3, 2, 1));
        InOrder order = inOrder(articleContentTranslationService);
        order.verify(articleContentTranslationService)
                .translateContent(firstArticle, TranslationLanguage.ZH_CN);
        order.verify(articleContentTranslationService)
                .translateContent(failingArticle, TranslationLanguage.ZH_CN);
        order.verify(articleContentTranslationService)
                .translateContent(lastArticle, TranslationLanguage.ZH_CN);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(articleTranslationRepository).findContentTranslationBackfillCandidates(
                eq(SourceLanguage.EN),
                eq(TranslationLanguage.ZH_CN),
                eq(TranslationStatus.SUCCESS),
                eq(List.of(ContentTranslationStatus.PENDING, ContentTranslationStatus.FAILED)),
                pageableCaptor.capture()
        );
        assertThat(pageableCaptor.getValue().getPageNumber()).isZero();
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(3);
    }

    @Test
    void returnsZeroCountsWhenNoCandidatesExist() {
        when(articleTranslationRepository.findContentTranslationBackfillCandidates(
                eq(SourceLanguage.EN),
                eq(TranslationLanguage.ZH_CN),
                eq(TranslationStatus.SUCCESS),
                eq(List.of(ContentTranslationStatus.PENDING, ContentTranslationStatus.FAILED)),
                any(Pageable.class)
        )).thenReturn(List.of());

        assertThat(service().backfill(1))
                .isEqualTo(new ArticleContentTranslationBackfillResult(0, 0, 0));
        verifyNoInteractions(articleContentTranslationService);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 11})
    void rejectsLimitsOutsideTheBoundedRange(int limit) {
        assertThatThrownBy(() -> service().backfill(limit))
                .isInstanceOf(InvalidArticleContentTranslationBackfillLimitException.class)
                .hasMessage(
                        "Article content translation backfill limit must be between 1 and 10"
                );

        verifyNoInteractions(articleTranslationRepository, articleContentTranslationService);
    }

    private ArticleContentTranslationBackfillService service() {
        return new ArticleContentTranslationBackfillService(
                articleTranslationRepository,
                articleContentTranslationService
        );
    }

    private ArticleTranslation translation(Article article) {
        ArticleTranslation translation = new ArticleTranslation(
                article,
                TranslationLanguage.ZH_CN
        );
        translation.setStatus(TranslationStatus.SUCCESS);
        return translation;
    }
}
