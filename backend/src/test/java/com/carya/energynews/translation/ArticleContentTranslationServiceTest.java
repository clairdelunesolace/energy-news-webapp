package com.carya.energynews.translation;

import com.carya.energynews.article.Article;
import com.carya.energynews.source.Source;
import com.carya.energynews.source.SourceLanguage;
import com.carya.energynews.source.SourcePriority;
import com.carya.energynews.source.SourceType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
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
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ArticleContentTranslationServiceTest {

    private static final Instant TITLE_TRANSLATED_AT = Instant.parse("2026-08-20T02:00:00Z");

    @Mock
    private ArticleTranslationRepository articleTranslationRepository;

    @Mock
    private TranslationProvider translationProvider;

    @Test
    void translatesContentOnlyAndStoresAnIndependentSuccessfulLifecycle() {
        Article article = article("Original first paragraph.\n\nOriginal second paragraph.");
        ArticleTranslation translation = successfulTitleTranslation(article);
        when(articleTranslationRepository.findByArticleIdAndLanguage(
                11L,
                TranslationLanguage.ZH_CN
        )).thenReturn(Optional.of(translation));
        assertPendingOnFirstSave(translation);
        when(translationProvider.translate(any(TranslationInput.class)))
                .thenReturn(new TranslationOutput(null, null, "中文第一段。\n\n中文第二段。"));
        Instant beforeTranslation = Instant.now();

        ArticleTranslation result = service().translateContent(
                article,
                TranslationLanguage.ZH_CN
        );

        Instant afterTranslation = Instant.now();
        ArgumentCaptor<TranslationInput> inputCaptor = ArgumentCaptor.forClass(TranslationInput.class);
        verify(translationProvider).translate(inputCaptor.capture());
        assertThat(inputCaptor.getValue()).isEqualTo(new TranslationInput(
                SourceLanguage.EN,
                TranslationLanguage.ZH_CN,
                null,
                null,
                article.getContent()
        ));
        assertThat(result.getContent()).isEqualTo("中文第一段。\n\n中文第二段。");
        assertThat(result.getContentStatus()).isEqualTo(ContentTranslationStatus.SUCCESS);
        assertThat(result.getContentTranslatedAt()).isBetween(beforeTranslation, afterTranslation);
        assertTitleTranslationUnchanged(result);
        verify(articleTranslationRepository, times(2)).saveAndFlush(same(translation));
        InOrder lifecycle = inOrder(articleTranslationRepository, translationProvider);
        lifecycle.verify(articleTranslationRepository).saveAndFlush(same(translation));
        lifecycle.verify(translationProvider).translate(any(TranslationInput.class));
        lifecycle.verify(articleTranslationRepository).saveAndFlush(same(translation));
    }

    @Test
    void returnsExistingSuccessfulContentWithoutCallingProvider() {
        Article article = article("Original content");
        ArticleTranslation translation = successfulTitleTranslation(article);
        translation.setContent("现有中文正文");
        translation.setContentStatus(ContentTranslationStatus.SUCCESS);
        translation.setContentTranslatedAt(Instant.parse("2026-08-20T03:00:00Z"));
        when(articleTranslationRepository.findByArticleIdAndLanguage(
                11L,
                TranslationLanguage.ZH_CN
        )).thenReturn(Optional.of(translation));

        ArticleTranslation result = service().translateContent(
                article,
                TranslationLanguage.ZH_CN
        );

        assertThat(result).isSameAs(translation);
        verifyNoInteractions(translationProvider);
        verify(articleTranslationRepository, never()).saveAndFlush(any());
    }

    @ParameterizedTest
    @EnumSource(
            value = ContentTranslationStatus.class,
            names = {"PENDING", "FAILED"}
    )
    void retriesPendingAndFailedContentTranslations(ContentTranslationStatus existingStatus) {
        Article article = article("Original content for retry");
        ArticleTranslation translation = successfulTitleTranslation(article);
        translation.setContent("Stale translated content");
        translation.setContentStatus(existingStatus);
        translation.setContentTranslatedAt(Instant.parse("2026-08-20T03:00:00Z"));
        when(articleTranslationRepository.findByArticleIdAndLanguage(
                11L,
                TranslationLanguage.ZH_CN
        )).thenReturn(Optional.of(translation));
        assertPendingOnFirstSave(translation);
        when(translationProvider.translate(any(TranslationInput.class)))
                .thenReturn(new TranslationOutput(null, null, "重试后的中文正文"));

        ArticleTranslation result = service().translateContent(
                article,
                TranslationLanguage.ZH_CN
        );

        assertThat(result.getContent()).isEqualTo("重试后的中文正文");
        assertThat(result.getContentStatus()).isEqualTo(ContentTranslationStatus.SUCCESS);
        assertTitleTranslationUnchanged(result);
    }

    @Test
    void storesFailedContentStateAndPreservesTitleTranslationOnProviderFailure() {
        Article article = article("Original content");
        ArticleTranslation translation = successfulTitleTranslation(article);
        when(articleTranslationRepository.findByArticleIdAndLanguage(
                11L,
                TranslationLanguage.ZH_CN
        )).thenReturn(Optional.of(translation));
        saveArgumentsAsProvided();
        TranslationException providerFailure = new TranslationException("Provider unavailable");
        when(translationProvider.translate(any(TranslationInput.class)))
                .thenThrow(providerFailure);

        assertThatThrownBy(() -> service().translateContent(
                article,
                TranslationLanguage.ZH_CN
        )).isSameAs(providerFailure);

        assertThat(translation.getContent()).isNull();
        assertThat(translation.getContentStatus()).isEqualTo(ContentTranslationStatus.FAILED);
        assertThat(translation.getContentTranslatedAt()).isNull();
        assertTitleTranslationUnchanged(translation);
        verify(articleTranslationRepository, times(2)).saveAndFlush(same(translation));
    }

    @Test
    void treatsBlankTranslatedContentAsFailure() {
        Article article = article("Original content");
        ArticleTranslation translation = successfulTitleTranslation(article);
        when(articleTranslationRepository.findByArticleIdAndLanguage(
                11L,
                TranslationLanguage.ZH_CN
        )).thenReturn(Optional.of(translation));
        saveArgumentsAsProvided();
        when(translationProvider.translate(any(TranslationInput.class)))
                .thenReturn(new TranslationOutput(null, null, "   "));

        assertThatThrownBy(() -> service().translateContent(
                article,
                TranslationLanguage.ZH_CN
        )).isInstanceOf(TranslationException.class)
                .hasMessage("Translation provider returned no translated content");

        assertThat(translation.getContentStatus()).isEqualTo(ContentTranslationStatus.FAILED);
        assertThat(translation.getContent()).isNull();
        assertTitleTranslationUnchanged(translation);
    }

    @Test
    void rejectsArticleWithoutOriginalContent() {
        Article article = article("   ");

        assertThatThrownBy(() -> service().translateContent(
                article,
                TranslationLanguage.ZH_CN
        )).isInstanceOf(TranslationException.class)
                .hasMessage("Article content is required for translation");

        verifyNoInteractions(articleTranslationRepository, translationProvider);
    }

    @Test
    void rejectsMissingTitleTranslationWithoutCreatingOne() {
        Article article = article("Original content");
        when(articleTranslationRepository.findByArticleIdAndLanguage(
                11L,
                TranslationLanguage.ZH_CN
        )).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().translateContent(
                article,
                TranslationLanguage.ZH_CN
        )).isInstanceOf(TranslationException.class)
                .hasMessage("A successful title and description translation is required");

        verify(articleTranslationRepository, never()).saveAndFlush(any());
        verifyNoInteractions(translationProvider);
    }

    @ParameterizedTest
    @EnumSource(value = TranslationStatus.class, names = {"PENDING", "FAILED"})
    void rejectsTitleTranslationThatIsNotSuccessful(TranslationStatus status) {
        Article article = article("Original content");
        ArticleTranslation translation = successfulTitleTranslation(article);
        translation.setStatus(status);
        when(articleTranslationRepository.findByArticleIdAndLanguage(
                11L,
                TranslationLanguage.ZH_CN
        )).thenReturn(Optional.of(translation));

        assertThatThrownBy(() -> service().translateContent(
                article,
                TranslationLanguage.ZH_CN
        )).isInstanceOf(TranslationException.class)
                .hasMessage(
                        "Title and description translation must be SUCCESS before translating content"
                );

        verify(articleTranslationRepository, never()).saveAndFlush(any());
        verifyNoInteractions(translationProvider);
    }

    private ArticleContentTranslationService service() {
        return new ArticleContentTranslationService(
                articleTranslationRepository,
                translationProvider
        );
    }

    private Article article(String content) {
        Source source = new Source(
                "English source",
                "https://example.com/source",
                SourceType.RSS,
                SourcePriority.MEDIUM,
                SourceLanguage.EN
        );
        Article article = new Article(
                "Original title",
                "https://example.com/article",
                source,
                Instant.parse("2026-08-20T01:00:00Z")
        );
        article.setContent(content);
        ReflectionTestUtils.setField(article, "id", 11L);
        return article;
    }

    private ArticleTranslation successfulTitleTranslation(Article article) {
        ArticleTranslation translation = new ArticleTranslation(
                article,
                TranslationLanguage.ZH_CN
        );
        translation.setTitle("现有中文标题");
        translation.setDescription("现有中文摘要");
        translation.setStatus(TranslationStatus.SUCCESS);
        translation.setTranslatedAt(TITLE_TRANSLATED_AT);
        return translation;
    }

    private void assertTitleTranslationUnchanged(ArticleTranslation translation) {
        assertThat(translation.getTitle()).isEqualTo("现有中文标题");
        assertThat(translation.getDescription()).isEqualTo("现有中文摘要");
        assertThat(translation.getStatus()).isEqualTo(TranslationStatus.SUCCESS);
        assertThat(translation.getTranslatedAt()).isEqualTo(TITLE_TRANSLATED_AT);
    }

    private void saveArgumentsAsProvided() {
        when(articleTranslationRepository.saveAndFlush(any(ArticleTranslation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private void assertPendingOnFirstSave(ArticleTranslation translation) {
        AtomicInteger saveCount = new AtomicInteger();
        when(articleTranslationRepository.saveAndFlush(same(translation))).thenAnswer(invocation -> {
            if (saveCount.getAndIncrement() == 0) {
                assertThat(translation.getContent()).isNull();
                assertThat(translation.getContentStatus())
                        .isEqualTo(ContentTranslationStatus.PENDING);
                assertThat(translation.getContentTranslatedAt()).isNull();
                assertTitleTranslationUnchanged(translation);
            }
            return translation;
        });
    }
}
