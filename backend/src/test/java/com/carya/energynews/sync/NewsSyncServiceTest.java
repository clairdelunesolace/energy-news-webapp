package com.carya.energynews.sync;

import com.carya.energynews.article.Article;
import com.carya.energynews.article.ArticleIngestionResult;
import com.carya.energynews.article.ArticleIngestionService;
import com.carya.energynews.collection.CollectedArticle;
import com.carya.energynews.collection.NewsCollectionException;
import com.carya.energynews.collection.NewsCollectionService;
import com.carya.energynews.filter.ArticleFilter;
import com.carya.energynews.filter.FilterResult;
import com.carya.energynews.source.Source;
import com.carya.energynews.source.SourceLanguage;
import com.carya.energynews.source.SourcePriority;
import com.carya.energynews.source.SourceRepository;
import com.carya.energynews.source.SourceType;
import com.carya.energynews.translation.ArticleTranslation;
import com.carya.energynews.translation.ArticleTranslationRepository;
import com.carya.energynews.translation.TranslationException;
import com.carya.energynews.translation.TranslationLanguage;
import com.carya.energynews.translation.TranslationProvider;
import com.carya.energynews.translation.TranslationService;
import com.carya.energynews.translation.TranslationStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NewsSyncServiceTest {

    @Mock
    private SourceRepository sourceRepository;

    @Mock
    private NewsCollectionService newsCollectionService;

    @Mock
    private ArticleFilter articleFilter;

    @Mock
    private ArticleIngestionService articleIngestionService;

    @Mock
    private TranslationService translationService;

    @Mock
    private ArticleTranslationRepository articleTranslationRepository;

    @Mock
    private TranslationProvider translationProvider;

    @InjectMocks
    private NewsSyncService newsSyncService;

    @Test
    void savesAndTranslatesAcceptedNewEnglishArticleAfterFiltering() {
        Source source = source("RSS source", SourceType.RSS, SourceLanguage.EN);
        CollectedArticle collected = collectedArticle("Accepted article");
        Article persisted = persistedArticle(source, "Accepted article", 11L);
        when(newsCollectionService.collect(source)).thenReturn(List.of(collected));
        when(articleFilter.evaluate(collected)).thenReturn(accepted());
        when(articleIngestionService.ingestAll(List.of(collected))).thenReturn(List.of(
                ArticleIngestionResult.saved(persisted)
        ));
        when(translationService.translate(persisted, TranslationLanguage.ZH_CN))
                .thenReturn(successfulTranslation(persisted));

        NewsSyncResult result = newsSyncService.sync(source);

        assertThat(result).isEqualTo(new NewsSyncResult(1, 0, 1, 0, 1, 0, 0));
        InOrder flow = inOrder(articleFilter, articleIngestionService, translationService);
        flow.verify(articleFilter).evaluate(collected);
        flow.verify(articleIngestionService).ingestAll(List.of(collected));
        flow.verify(translationService).translate(persisted, TranslationLanguage.ZH_CN);
    }

    @Test
    void rejectedArticleIsNeverIngestedOrTranslated() {
        Source source = source("RSS source", SourceType.RSS, SourceLanguage.EN);
        CollectedArticle collected = collectedArticle("Rejected article");
        when(newsCollectionService.collect(source)).thenReturn(List.of(collected));
        when(articleFilter.evaluate(collected)).thenReturn(rejected());
        when(articleIngestionService.ingestAll(List.of())).thenReturn(List.of());

        NewsSyncResult result = newsSyncService.sync(source);

        assertThat(result).isEqualTo(new NewsSyncResult(1, 1, 0, 0, 0, 0, 0));
        verify(articleIngestionService).ingestAll(List.of());
        verifyNoInteractions(translationService);
    }

    @Test
    void translatesCanonicalArticleForDuplicateEnglishArticle() {
        Source source = source("RSS source", SourceType.RSS, SourceLanguage.EN);
        CollectedArticle collected = collectedArticle("Duplicate article");
        Article existing = persistedArticle(source, "Existing article", 12L);
        when(newsCollectionService.collect(source)).thenReturn(List.of(collected));
        when(articleFilter.evaluate(collected)).thenReturn(accepted());
        when(articleIngestionService.ingestAll(List.of(collected))).thenReturn(List.of(
                ArticleIngestionResult.duplicate(existing)
        ));
        when(translationService.translate(existing, TranslationLanguage.ZH_CN))
                .thenReturn(successfulTranslation(existing));

        NewsSyncResult result = newsSyncService.sync(source);

        assertThat(result).isEqualTo(new NewsSyncResult(1, 0, 0, 1, 1, 0, 0));
        verify(translationService).translate(existing, TranslationLanguage.ZH_CN);
    }

    @Test
    void duplicateWithExistingSuccessfulTranslationDoesNotCallProviderAgain() {
        Source source = source("RSS source", SourceType.RSS, SourceLanguage.EN);
        CollectedArticle collected = collectedArticle("Already translated duplicate");
        Article existingArticle = persistedArticle(source, "Existing article", 13L);
        ArticleTranslation existingTranslation = successfulTranslation(existingArticle);
        when(newsCollectionService.collect(source)).thenReturn(List.of(collected));
        when(articleFilter.evaluate(collected)).thenReturn(accepted());
        when(articleIngestionService.ingestAll(List.of(collected))).thenReturn(List.of(
                ArticleIngestionResult.duplicate(existingArticle)
        ));
        when(articleTranslationRepository.findByArticleIdAndLanguage(13L, TranslationLanguage.ZH_CN))
                .thenReturn(Optional.of(existingTranslation));
        TranslationService actualTranslationService = new TranslationService(
                articleTranslationRepository,
                translationProvider
        );
        NewsSyncService service = new NewsSyncService(
                sourceRepository,
                newsCollectionService,
                articleFilter,
                articleIngestionService,
                actualTranslationService
        );

        NewsSyncResult result = service.sync(source);

        assertThat(result).isEqualTo(new NewsSyncResult(1, 0, 0, 1, 1, 0, 0));
        verifyNoInteractions(translationProvider);
        verify(articleTranslationRepository, never()).saveAndFlush(existingTranslation);
    }

    @Test
    void skipsTranslationForChineseSourceArticle() {
        Source source = source("Chinese RSS source", SourceType.RSS, SourceLanguage.ZH_CN);
        CollectedArticle collected = collectedArticle("Chinese article");
        Article persisted = persistedArticle(source, "Chinese article", 14L);
        when(newsCollectionService.collect(source)).thenReturn(List.of(collected));
        when(articleFilter.evaluate(collected)).thenReturn(accepted());
        when(articleIngestionService.ingestAll(List.of(collected))).thenReturn(List.of(
                ArticleIngestionResult.saved(persisted)
        ));

        NewsSyncResult result = newsSyncService.sync(source);

        assertThat(result).isEqualTo(new NewsSyncResult(1, 0, 1, 0, 0, 0, 0));
        verifyNoInteractions(translationService);
    }

    @Test
    void translationFailureKeepsIngestionSuccessfulAndDoesNotFailSource() {
        Source source = source("RSS source", SourceType.RSS, SourceLanguage.EN);
        CollectedArticle collected = collectedArticle("Saved despite translation failure");
        Article persisted = persistedArticle(source, "Saved article", 15L);
        when(newsCollectionService.collect(source)).thenReturn(List.of(collected));
        when(articleFilter.evaluate(collected)).thenReturn(accepted());
        when(articleIngestionService.ingestAll(List.of(collected))).thenReturn(List.of(
                ArticleIngestionResult.saved(persisted)
        ));
        when(translationService.translate(persisted, TranslationLanguage.ZH_CN))
                .thenThrow(new TranslationException("DeepL API key is not configured"));

        NewsSyncResult result = newsSyncService.sync(source);

        assertThat(result).isEqualTo(new NewsSyncResult(1, 0, 1, 0, 0, 1, 0));
        assertThat(result.saved()).isEqualTo(1);
        assertThat(result.failedSources()).isZero();
        verify(articleIngestionService).ingestAll(List.of(collected));
    }

    @Test
    void failedTranslationDoesNotStopLaterArticles() {
        Source source = source("RSS source", SourceType.RSS, SourceLanguage.EN);
        CollectedArticle firstCollected = collectedArticle("First article");
        CollectedArticle secondCollected = collectedArticle("Second article");
        Article firstPersisted = persistedArticle(source, "First article", 16L);
        Article secondPersisted = persistedArticle(source, "Second article", 17L);
        when(newsCollectionService.collect(source)).thenReturn(List.of(firstCollected, secondCollected));
        when(articleFilter.evaluate(firstCollected)).thenReturn(accepted());
        when(articleFilter.evaluate(secondCollected)).thenReturn(accepted());
        when(articleIngestionService.ingestAll(List.of(firstCollected, secondCollected))).thenReturn(List.of(
                ArticleIngestionResult.saved(firstPersisted),
                ArticleIngestionResult.saved(secondPersisted)
        ));
        when(translationService.translate(firstPersisted, TranslationLanguage.ZH_CN))
                .thenThrow(new TranslationException("Provider unavailable"));
        when(translationService.translate(secondPersisted, TranslationLanguage.ZH_CN))
                .thenReturn(successfulTranslation(secondPersisted));

        NewsSyncResult result = newsSyncService.sync(source);

        assertThat(result).isEqualTo(new NewsSyncResult(2, 0, 2, 0, 1, 1, 0));
        verify(translationService).translate(firstPersisted, TranslationLanguage.ZH_CN);
        verify(translationService).translate(secondPersisted, TranslationLanguage.ZH_CN);
    }

    @Test
    void aggregatesTranslationCountersAcrossMultipleSources() {
        Source firstSource = source("First RSS source", SourceType.RSS, SourceLanguage.EN);
        Source secondSource = source("Second RSS source", SourceType.RSS, SourceLanguage.EN);
        CollectedArticle firstCollected = collectedArticle("First article");
        CollectedArticle secondCollected = collectedArticle("Second article");
        Article firstPersisted = persistedArticle(firstSource, "First article", 18L);
        Article secondPersisted = persistedArticle(secondSource, "Second article", 19L);
        when(sourceRepository.findAllByEnabledTrue()).thenReturn(List.of(firstSource, secondSource));
        when(newsCollectionService.collect(firstSource)).thenReturn(List.of(firstCollected));
        when(newsCollectionService.collect(secondSource)).thenReturn(List.of(secondCollected));
        when(articleFilter.evaluate(firstCollected)).thenReturn(accepted());
        when(articleFilter.evaluate(secondCollected)).thenReturn(accepted());
        when(articleIngestionService.ingestAll(List.of(firstCollected))).thenReturn(List.of(
                ArticleIngestionResult.saved(firstPersisted)
        ));
        when(articleIngestionService.ingestAll(List.of(secondCollected))).thenReturn(List.of(
                ArticleIngestionResult.duplicate(secondPersisted)
        ));
        when(translationService.translate(firstPersisted, TranslationLanguage.ZH_CN))
                .thenReturn(successfulTranslation(firstPersisted));
        when(translationService.translate(secondPersisted, TranslationLanguage.ZH_CN))
                .thenThrow(new TranslationException("Provider unavailable"));

        NewsSyncResult result = newsSyncService.syncAllEnabledSources();

        assertThat(result).isEqualTo(new NewsSyncResult(2, 0, 1, 1, 1, 1, 0));
    }

    @Test
    void keepsCollectionFailuresSeparateFromTranslationFailures() {
        Source failingSource = source("Failing RSS source", SourceType.RSS, SourceLanguage.EN);
        Source healthySource = source("Healthy RSS source", SourceType.RSS, SourceLanguage.EN);
        CollectedArticle collected = collectedArticle("Healthy article");
        Article persisted = persistedArticle(healthySource, "Healthy article", 20L);
        when(sourceRepository.findAllByEnabledTrue()).thenReturn(List.of(failingSource, healthySource));
        when(newsCollectionService.collect(failingSource))
                .thenThrow(new NewsCollectionException("Unable to fetch RSS feed"));
        when(newsCollectionService.collect(healthySource)).thenReturn(List.of(collected));
        when(articleFilter.evaluate(collected)).thenReturn(accepted());
        when(articleIngestionService.ingestAll(List.of(collected))).thenReturn(List.of(
                ArticleIngestionResult.saved(persisted)
        ));
        when(translationService.translate(persisted, TranslationLanguage.ZH_CN))
                .thenThrow(new TranslationException("Provider unavailable"));

        NewsSyncResult result = newsSyncService.syncAllEnabledSources();

        assertThat(result).isEqualTo(new NewsSyncResult(1, 0, 1, 0, 0, 1, 1));
    }

    @Test
    void returnsZeroCountsForEmptyFeed() {
        Source source = source("Empty RSS source", SourceType.RSS, SourceLanguage.EN);
        when(newsCollectionService.collect(source)).thenReturn(List.of());
        when(articleIngestionService.ingestAll(List.of())).thenReturn(List.of());

        NewsSyncResult result = newsSyncService.sync(source);

        assertThat(result).isEqualTo(new NewsSyncResult(0, 0, 0, 0, 0, 0, 0));
        verifyNoInteractions(translationService);
    }

    @Test
    void skipsUnsupportedSourcesDuringAllSourceSync() {
        Source rssSource = source("RSS source", SourceType.RSS, SourceLanguage.EN);
        Source apiSource = source("API source", SourceType.API, SourceLanguage.EN);
        Source websiteSource = source("Website source", SourceType.WEBSITE, SourceLanguage.EN);
        when(sourceRepository.findAllByEnabledTrue()).thenReturn(List.of(
                rssSource,
                apiSource,
                websiteSource
        ));
        when(newsCollectionService.collect(rssSource)).thenReturn(List.of());
        when(articleIngestionService.ingestAll(List.of())).thenReturn(List.of());

        NewsSyncResult result = newsSyncService.syncAllEnabledSources();

        assertThat(result).isEqualTo(new NewsSyncResult(0, 0, 0, 0, 0, 0, 0));
        verify(newsCollectionService).collect(rssSource);
        verify(newsCollectionService, never()).collect(apiSource);
        verify(newsCollectionService, never()).collect(websiteSource);
    }

    private Source source(String name, SourceType type, SourceLanguage language) {
        return new Source(
                name,
                "https://example.com/" + name.replace(' ', '-').toLowerCase(),
                type,
                SourcePriority.MEDIUM,
                language
        );
    }

    private CollectedArticle collectedArticle(String title) {
        return new CollectedArticle(
                title,
                "https://example.com/articles/" + title.replace(' ', '-').toLowerCase(),
                null,
                null,
                null,
                null
        );
    }

    private Article persistedArticle(Source source, String title, Long id) {
        Article article = new Article(
                title,
                "https://example.com/persisted/" + id,
                source,
                Instant.parse("2026-08-20T01:00:00Z")
        );
        ReflectionTestUtils.setField(article, "id", id);
        return article;
    }

    private ArticleTranslation successfulTranslation(Article article) {
        ArticleTranslation translation = new ArticleTranslation(article, TranslationLanguage.ZH_CN);
        translation.setTitle("中文标题");
        translation.setStatus(TranslationStatus.SUCCESS);
        translation.setTranslatedAt(Instant.parse("2026-08-20T02:00:00Z"));
        return translation;
    }

    private FilterResult accepted() {
        return new FilterResult(true, "Matched keyword");
    }

    private FilterResult rejected() {
        return new FilterResult(false, "No configured keyword matched");
    }
}
