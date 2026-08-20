package com.carya.energynews.translation;

import com.carya.energynews.article.Article;
import com.carya.energynews.article.ArticleRepository;
import com.carya.energynews.source.Source;
import com.carya.energynews.source.SourceLanguage;
import com.carya.energynews.source.SourcePriority;
import com.carya.energynews.source.SourceType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
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
class TranslationBackfillServiceTest {

    @Mock
    private ArticleRepository articleRepository;

    @Mock
    private TranslationService translationService;

    @InjectMocks
    private TranslationBackfillService translationBackfillService;

    @Test
    void countsSuccessesAndFailuresAndContinuesAfterOneArticleFails() {
        Article first = article(11L);
        Article failing = article(12L);
        Article last = article(13L);
        when(articleRepository.findTranslationBackfillCandidates(
                eq(SourceLanguage.EN),
                eq(TranslationLanguage.ZH_CN),
                eq(TranslationStatus.SUCCESS),
                org.mockito.ArgumentMatchers.any(Pageable.class)
        )).thenReturn(List.of(first, failing, last));
        when(translationService.translate(any(Article.class), eq(TranslationLanguage.ZH_CN)))
                .thenAnswer(invocation -> {
                    if (invocation.getArgument(0) == failing) {
                        throw new TranslationException("Provider unavailable");
                    }
                    return null;
                });

        TranslationBackfillResult result = translationBackfillService.backfill(3);

        assertThat(result).isEqualTo(new TranslationBackfillResult(3, 2, 1));
        InOrder translationOrder = inOrder(translationService);
        translationOrder.verify(translationService).translate(first, TranslationLanguage.ZH_CN);
        translationOrder.verify(translationService).translate(failing, TranslationLanguage.ZH_CN);
        translationOrder.verify(translationService).translate(last, TranslationLanguage.ZH_CN);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(articleRepository).findTranslationBackfillCandidates(
                eq(SourceLanguage.EN),
                eq(TranslationLanguage.ZH_CN),
                eq(TranslationStatus.SUCCESS),
                pageableCaptor.capture()
        );
        assertThat(pageableCaptor.getValue().getPageNumber()).isZero();
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(3);
    }

    @Test
    void returnsZeroCountersWhenThereAreNoCandidates() {
        when(articleRepository.findTranslationBackfillCandidates(
                eq(SourceLanguage.EN),
                eq(TranslationLanguage.ZH_CN),
                eq(TranslationStatus.SUCCESS),
                org.mockito.ArgumentMatchers.any(Pageable.class)
        )).thenReturn(List.of());

        TranslationBackfillResult result = translationBackfillService.backfill(20);

        assertThat(result).isEqualTo(new TranslationBackfillResult(0, 0, 0));
        verifyNoInteractions(translationService);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 101})
    void rejectsLimitsOutsideTheBoundedRange(int limit) {
        assertThatThrownBy(() -> translationBackfillService.backfill(limit))
                .isInstanceOf(InvalidTranslationBackfillLimitException.class)
                .hasMessage("Translation backfill limit must be between 1 and 100");

        verifyNoInteractions(articleRepository, translationService);
    }

    private Article article(Long id) {
        Source source = new Source(
                "English source",
                "https://example.com/source/" + id,
                SourceType.RSS,
                SourcePriority.MEDIUM,
                SourceLanguage.EN
        );
        Article article = new Article(
                "Article " + id,
                "https://example.com/article/" + id,
                source,
                Instant.parse("2026-08-20T01:00:00Z")
        );
        ReflectionTestUtils.setField(article, "id", id);
        return article;
    }
}
