package com.carya.energynews.translation;

import com.carya.energynews.article.Article;
import com.carya.energynews.source.Source;
import com.carya.energynews.source.SourceLanguage;
import com.carya.energynews.source.SourcePriority;
import com.carya.energynews.source.SourceType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.inOrder;

@ExtendWith(MockitoExtension.class)
class TranslationServiceTest {

    @Mock
    private ArticleTranslationRepository articleTranslationRepository;

    @Mock
    private TranslationProvider translationProvider;

    @InjectMocks
    private TranslationService translationService;

    @Test
    void translatesNewEnglishArticleSuccessfully() {
        Article article = article(SourceLanguage.EN, "Original description");
        when(articleTranslationRepository.findByArticleIdAndLanguage(11L, TranslationLanguage.ZH_CN))
                .thenReturn(Optional.empty());
        saveArgumentsAsProvided();
        when(translationProvider.translate(any(TranslationInput.class))).thenReturn(
                new TranslationOutput("中文标题", "中文描述")
        );
        Instant beforeTranslation = Instant.now();

        ArticleTranslation result = translationService.translate(article, TranslationLanguage.ZH_CN);

        Instant afterTranslation = Instant.now();
        ArgumentCaptor<TranslationInput> inputCaptor = ArgumentCaptor.forClass(TranslationInput.class);
        verify(translationProvider).translate(inputCaptor.capture());
        assertThat(inputCaptor.getValue()).isEqualTo(new TranslationInput(
                SourceLanguage.EN,
                TranslationLanguage.ZH_CN,
                "Original title",
                "Original description"
        ));
        assertThat(result.getArticle()).isSameAs(article);
        assertThat(result.getLanguage()).isEqualTo(TranslationLanguage.ZH_CN);
        assertThat(result.getTitle()).isEqualTo("中文标题");
        assertThat(result.getDescription()).isEqualTo("中文描述");
        assertThat(result.getStatus()).isEqualTo(TranslationStatus.SUCCESS);
        assertThat(result.getTranslatedAt()).isBetween(beforeTranslation, afterTranslation);
        verify(articleTranslationRepository, times(2)).saveAndFlush(same(result));
        InOrder lifecycle = inOrder(articleTranslationRepository, translationProvider);
        lifecycle.verify(articleTranslationRepository).saveAndFlush(same(result));
        lifecycle.verify(translationProvider).translate(any(TranslationInput.class));
        lifecycle.verify(articleTranslationRepository).saveAndFlush(same(result));
    }

    @Test
    void supportsNullArticleDescription() {
        Article article = article(SourceLanguage.EN, null);
        when(articleTranslationRepository.findByArticleIdAndLanguage(11L, TranslationLanguage.ZH_CN))
                .thenReturn(Optional.empty());
        saveArgumentsAsProvided();
        when(translationProvider.translate(any(TranslationInput.class))).thenReturn(
                new TranslationOutput("中文标题", null)
        );

        ArticleTranslation result = translationService.translate(article, TranslationLanguage.ZH_CN);

        ArgumentCaptor<TranslationInput> inputCaptor = ArgumentCaptor.forClass(TranslationInput.class);
        verify(translationProvider).translate(inputCaptor.capture());
        assertThat(inputCaptor.getValue().description()).isNull();
        assertThat(result.getDescription()).isNull();
        assertThat(result.getStatus()).isEqualTo(TranslationStatus.SUCCESS);
    }

    @Test
    void returnsExistingSuccessfulTranslationWithoutCallingProviderAgain() {
        Article article = article(SourceLanguage.EN, "Original description");
        ArticleTranslation existing = translation(article, TranslationStatus.SUCCESS);
        existing.setTitle("现有标题");
        existing.setDescription("现有描述");
        existing.setTranslatedAt(Instant.parse("2026-08-20T03:00:00Z"));
        when(articleTranslationRepository.findByArticleIdAndLanguage(11L, TranslationLanguage.ZH_CN))
                .thenReturn(Optional.of(existing));

        ArticleTranslation result = translationService.translate(article, TranslationLanguage.ZH_CN);

        assertThat(result).isSameAs(existing);
        assertThat(result.getTitle()).isEqualTo("现有标题");
        verifyNoInteractions(translationProvider);
        verify(articleTranslationRepository, never()).saveAndFlush(any(ArticleTranslation.class));
    }

    @Test
    void storesFailedStateAndRethrowsWithoutModifyingOriginalArticle() {
        Article article = article(SourceLanguage.EN, "Original description");
        when(articleTranslationRepository.findByArticleIdAndLanguage(11L, TranslationLanguage.ZH_CN))
                .thenReturn(Optional.empty());
        saveArgumentsAsProvided();
        TranslationException providerFailure = new TranslationException("Provider unavailable");
        when(translationProvider.translate(any(TranslationInput.class))).thenThrow(providerFailure);

        assertThatThrownBy(() -> translationService.translate(article, TranslationLanguage.ZH_CN))
                .isSameAs(providerFailure);

        ArgumentCaptor<ArticleTranslation> translationCaptor = ArgumentCaptor.forClass(ArticleTranslation.class);
        verify(articleTranslationRepository, times(2)).saveAndFlush(translationCaptor.capture());
        ArticleTranslation failed = translationCaptor.getAllValues().getLast();
        assertThat(failed.getStatus()).isEqualTo(TranslationStatus.FAILED);
        assertThat(failed.getTitle()).isNull();
        assertThat(failed.getDescription()).isNull();
        assertThat(failed.getTranslatedAt()).isNull();
        assertThat(article.getTitle()).isEqualTo("Original title");
        assertThat(article.getDescription()).isEqualTo("Original description");
    }

    @Test
    void retriesFailedTranslationUsingTheSameRowAndCanSucceed() {
        Article article = article(SourceLanguage.EN, "Original description");
        ArticleTranslation existing = translation(article, TranslationStatus.FAILED);
        existing.setTitle("Stale title");
        existing.setDescription("Stale description");
        existing.setTranslatedAt(Instant.parse("2026-08-20T03:00:00Z"));
        when(articleTranslationRepository.findByArticleIdAndLanguage(11L, TranslationLanguage.ZH_CN))
                .thenReturn(Optional.of(existing));
        assertPendingOnFirstSave(existing);
        when(translationProvider.translate(any(TranslationInput.class))).thenReturn(
                new TranslationOutput("重试标题", "重试描述")
        );

        ArticleTranslation result = translationService.translate(article, TranslationLanguage.ZH_CN);

        assertThat(result).isSameAs(existing);
        assertThat(result.getId()).isEqualTo(21L);
        assertThat(result.getStatus()).isEqualTo(TranslationStatus.SUCCESS);
        assertThat(result.getTitle()).isEqualTo("重试标题");
        assertThat(result.getDescription()).isEqualTo("重试描述");
        assertThat(result.getTranslatedAt()).isNotNull();
        verify(articleTranslationRepository, times(2)).saveAndFlush(same(existing));
    }

    @Test
    void retriesExistingPendingTranslationDeterministically() {
        Article article = article(SourceLanguage.EN, "Original description");
        ArticleTranslation existing = translation(article, TranslationStatus.PENDING);
        existing.setTitle("Unexpected partial title");
        existing.setDescription("Unexpected partial description");
        when(articleTranslationRepository.findByArticleIdAndLanguage(11L, TranslationLanguage.ZH_CN))
                .thenReturn(Optional.of(existing));
        assertPendingOnFirstSave(existing);
        when(translationProvider.translate(any(TranslationInput.class))).thenReturn(
                new TranslationOutput("恢复标题", "恢复描述")
        );

        ArticleTranslation result = translationService.translate(article, TranslationLanguage.ZH_CN);

        assertThat(result).isSameAs(existing);
        assertThat(result.getStatus()).isEqualTo(TranslationStatus.SUCCESS);
        assertThat(result.getTitle()).isEqualTo("恢复标题");
        verify(translationProvider).translate(any(TranslationInput.class));
    }

    @Test
    void rejectsChineseSourceWithoutCallingProviderOrCreatingTranslation() {
        Article article = article(SourceLanguage.ZH_CN, "中文描述");

        assertThatThrownBy(() -> translationService.translate(article, TranslationLanguage.ZH_CN))
                .isInstanceOf(TranslationException.class)
                .hasMessage("Article source is already ZH_CN; translation to ZH_CN is not required");

        verifyNoInteractions(articleTranslationRepository, translationProvider);
    }

    @Test
    void dependsOnTranslationProviderAbstraction() throws NoSuchMethodException {
        assertThat(TranslationService.class.getConstructor(
                ArticleTranslationRepository.class,
                TranslationProvider.class
        )).isNotNull();
    }

    private Article article(SourceLanguage language, String description) {
        Source source = new Source(
                "Translation source",
                "https://example.com/translation-source",
                SourceType.RSS,
                SourcePriority.MEDIUM,
                language
        );
        Article article = new Article(
                "Original title",
                "https://example.com/articles/original",
                source,
                Instant.parse("2026-08-20T01:00:00Z")
        );
        article.setDescription(description);
        ReflectionTestUtils.setField(article, "id", 11L);
        return article;
    }

    private ArticleTranslation translation(Article article, TranslationStatus status) {
        ArticleTranslation translation = new ArticleTranslation(article, TranslationLanguage.ZH_CN);
        translation.setStatus(status);
        ReflectionTestUtils.setField(translation, "id", 21L);
        return translation;
    }

    private void saveArgumentsAsProvided() {
        when(articleTranslationRepository.saveAndFlush(any(ArticleTranslation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private void assertPendingOnFirstSave(ArticleTranslation translation) {
        AtomicInteger saveCount = new AtomicInteger();
        when(articleTranslationRepository.saveAndFlush(same(translation))).thenAnswer(invocation -> {
            if (saveCount.getAndIncrement() == 0) {
                assertThat(translation.getStatus()).isEqualTo(TranslationStatus.PENDING);
                assertThat(translation.getTitle()).isNull();
                assertThat(translation.getDescription()).isNull();
                assertThat(translation.getTranslatedAt()).isNull();
            }
            return translation;
        });
    }
}
