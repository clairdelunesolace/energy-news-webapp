package com.carya.energynews.article;

import com.carya.energynews.content.ArticleContentFetchException;
import com.carya.energynews.content.ArticleContentService;
import com.carya.energynews.source.Source;
import com.carya.energynews.source.SourceLanguage;
import com.carya.energynews.source.SourcePriority;
import com.carya.energynews.source.SourceType;
import com.carya.energynews.translation.ArticleContentTranslationService;
import com.carya.energynews.translation.ArticleTranslation;
import com.carya.energynews.translation.ContentTranslationStatus;
import com.carya.energynews.translation.TranslationException;
import com.carya.energynews.translation.TranslationLanguage;
import com.carya.energynews.translation.TranslationService;
import com.carya.energynews.translation.TranslationStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ArticlePostProcessingServiceTest {

    @Mock
    private TranslationService translationService;

    @Mock
    private ArticleContentService articleContentService;

    @Mock
    private ArticleContentTranslationService contentTranslationService;

    @Test
    void processesEnglishMetadataThenOriginalContentThenTranslatedContent() {
        Article article = article(SourceLanguage.EN, 11L);
        when(translationService.translate(article, TranslationLanguage.ZH_CN))
                .thenReturn(metadataTranslation(article));
        when(articleContentService.enrichContent(article)).thenAnswer(invocation -> {
            article.setContent("Extracted original content");
            return article;
        });
        when(contentTranslationService.translateContent(
                article,
                TranslationLanguage.ZH_CN
        )).thenReturn(contentTranslation(article));

        ArticlePostProcessingResult result = service().process(article);

        assertThat(result).isEqualTo(new ArticlePostProcessingResult(
                true,
                false,
                true,
                false,
                true,
                false
        ));
        InOrder order = inOrder(
                translationService,
                articleContentService,
                contentTranslationService
        );
        order.verify(translationService).translate(article, TranslationLanguage.ZH_CN);
        order.verify(articleContentService).enrichContent(article);
        order.verify(contentTranslationService).translateContent(
                article,
                TranslationLanguage.ZH_CN
        );
        assertThat(article.getSource().isEnabled()).isFalse();
        assertThat(article.getSource().isContentEnrichmentEnabled()).isFalse();
    }

    @Test
    void metadataFailureKeepsArticleAndStillAttemptsOriginalContent() {
        Article article = article(SourceLanguage.EN, 12L);
        when(translationService.translate(article, TranslationLanguage.ZH_CN))
                .thenThrow(new TranslationException("DeepL unavailable"));
        when(articleContentService.enrichContent(article)).thenAnswer(invocation -> {
            article.setContent("Original content remains usable");
            return article;
        });

        ArticlePostProcessingResult result = service().process(article);

        assertThat(result).isEqualTo(new ArticlePostProcessingResult(
                false,
                true,
                true,
                false,
                false,
                false
        ));
        assertThat(article.getTitle()).isEqualTo("Original title 12");
        assertThat(article.getContent()).isEqualTo("Original content remains usable");
        verifyNoInteractions(contentTranslationService);
    }

    @Test
    void contentExtractionFailureKeepsSuccessfulMetadataAndSkipsContentTranslation() {
        Article article = article(SourceLanguage.EN, 13L);
        ArticleTranslation metadataTranslation = metadataTranslation(article);
        when(translationService.translate(article, TranslationLanguage.ZH_CN))
                .thenReturn(metadataTranslation);
        when(articleContentService.enrichContent(article))
                .thenThrow(new ArticleContentFetchException("Publisher blocked request"));

        ArticlePostProcessingResult result = service().process(article);

        assertThat(result).isEqualTo(new ArticlePostProcessingResult(
                true,
                false,
                false,
                true,
                false,
                false
        ));
        assertThat(article.getContent()).isNull();
        assertThat(metadataTranslation.getStatus()).isEqualTo(TranslationStatus.SUCCESS);
        assertThat(metadataTranslation.getTitle()).isEqualTo("中文标题");
        assertThat(metadataTranslation.getDescription()).isEqualTo("中文摘要");
        verifyNoInteractions(contentTranslationService);
    }

    @Test
    void contentTranslationFailureKeepsExtractedOriginalContent() {
        Article article = article(SourceLanguage.EN, 14L);
        ArticleTranslation metadataTranslation = metadataTranslation(article);
        when(translationService.translate(article, TranslationLanguage.ZH_CN))
                .thenReturn(metadataTranslation);
        when(articleContentService.enrichContent(article)).thenAnswer(invocation -> {
            article.setContent("Extracted original content");
            return article;
        });
        when(contentTranslationService.translateContent(
                article,
                TranslationLanguage.ZH_CN
        )).thenThrow(new TranslationException("DeepL content request failed"));

        ArticlePostProcessingResult result = service().process(article);

        assertThat(result).isEqualTo(new ArticlePostProcessingResult(
                true,
                false,
                true,
                false,
                false,
                true
        ));
        assertThat(article.getContent()).isEqualTo("Extracted original content");
        assertThat(metadataTranslation.getStatus()).isEqualTo(TranslationStatus.SUCCESS);
        assertThat(metadataTranslation.getTitle()).isEqualTo("中文标题");
        assertThat(metadataTranslation.getDescription()).isEqualTo("中文摘要");
    }

    @Test
    void chineseArticleSkipsTranslationButStillAttemptsExplicitContentExtraction() {
        Article article = article(SourceLanguage.ZH_CN, 15L);
        when(articleContentService.enrichContent(article)).thenAnswer(invocation -> {
            article.setContent("中文完整正文");
            return article;
        });

        ArticlePostProcessingResult result = service().process(article);

        assertThat(result).isEqualTo(new ArticlePostProcessingResult(
                false,
                false,
                true,
                false,
                false,
                false
        ));
        verifyNoInteractions(translationService, contentTranslationService);
        verify(articleContentService).enrichContent(article);
    }

    private ArticlePostProcessingService service() {
        return new ArticlePostProcessingService(
                translationService,
                articleContentService,
                contentTranslationService
        );
    }

    private Article article(SourceLanguage language, Long id) {
        Source source = new Source(
                "Discovery publisher",
                "https://example.com",
                SourceType.WEBSITE,
                SourcePriority.MEDIUM,
                language,
                false
        );
        source.setEnabled(false);
        Article article = new Article(
                "Original title " + id,
                "https://example.com/articles/" + id,
                source,
                Instant.parse("2026-08-28T02:00:00Z")
        );
        article.setDescription("Original description");
        ReflectionTestUtils.setField(article, "id", id);
        return article;
    }

    private ArticleTranslation metadataTranslation(Article article) {
        ArticleTranslation translation = new ArticleTranslation(
                article,
                TranslationLanguage.ZH_CN
        );
        translation.setTitle("中文标题");
        translation.setDescription("中文摘要");
        translation.setStatus(TranslationStatus.SUCCESS);
        return translation;
    }

    private ArticleTranslation contentTranslation(Article article) {
        ArticleTranslation translation = metadataTranslation(article);
        translation.setContent("中文完整正文");
        translation.setContentStatus(ContentTranslationStatus.SUCCESS);
        return translation;
    }
}
