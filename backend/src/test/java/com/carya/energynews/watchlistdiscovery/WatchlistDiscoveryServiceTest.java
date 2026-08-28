package com.carya.energynews.watchlistdiscovery;

import com.carya.energynews.article.Article;
import com.carya.energynews.article.ArticlePostProcessingResult;
import com.carya.energynews.article.ArticlePostProcessingService;
import com.carya.energynews.discovery.DiscoveredArticle;
import com.carya.energynews.discovery.NewsDiscoveryException;
import com.carya.energynews.discovery.NewsDiscoveryProvider;
import com.carya.energynews.discovery.NewsDiscoveryQuery;
import com.carya.energynews.discovery.NewsDiscoveryQueryFactory;
import com.carya.energynews.discovery.NewsDiscoveryService;
import com.carya.energynews.source.Source;
import com.carya.energynews.source.SourceLanguage;
import com.carya.energynews.source.SourcePriority;
import com.carya.energynews.source.SourceType;
import com.carya.energynews.watchlist.Keyword;
import com.carya.energynews.watchlist.Watchlist;
import com.carya.energynews.watchlist.WatchlistNotFoundException;
import com.carya.energynews.watchlist.WatchlistRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WatchlistDiscoveryServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-28T04:00:00Z");

    @Mock
    private WatchlistRepository watchlistRepository;

    @Mock
    private NewsDiscoveryProvider newsDiscoveryProvider;

    @Mock
    private WatchlistDiscoveryPersistenceService persistenceService;

    @Mock
    private ArticlePostProcessingService postProcessingService;

    private WatchlistDiscoveryService service;

    @BeforeEach
    void setUp() {
        service = service(Optional.of(new NewsDiscoveryService(newsDiscoveryProvider)));
    }

    @Test
    void rejectsUnknownAndDisabledWatchlistsBeforeDiscovery() {
        when(watchlistRepository.findWithKeywordsById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.run(request(99L, null, null, 10)))
                .isInstanceOf(WatchlistNotFoundException.class)
                .hasMessage("Watchlist with id 99 was not found");

        Watchlist disabled = watchlist(2L, "Disabled", false);
        when(watchlistRepository.findWithKeywordsById(2L)).thenReturn(Optional.of(disabled));
        assertThatThrownBy(() -> service.run(request(2L, null, null, 10)))
                .isInstanceOf(WatchlistDisabledException.class)
                .hasMessage("Watchlist with id 2 is disabled");
        verifyNoInteractions(newsDiscoveryProvider, persistenceService, postProcessingService);
    }

    @Test
    void noEnabledKeywordsReturnsSuccessfulNoOpWithoutProvider() {
        Watchlist watchlist = watchlist(1L, "No-op", true);
        keyword(watchlist, 11L, "disabled keyword", false);
        when(watchlistRepository.findWithKeywordsById(1L)).thenReturn(Optional.of(watchlist));
        WatchlistDiscoveryService noProviderService = service(Optional.empty());

        WatchlistDiscoveryRunResponse response = noProviderService.run(
                request(1L, null, null, 10)
        );

        assertThat(response.keywordsProcessed()).isZero();
        assertThat(response.keywordsFailed()).isZero();
        assertThat(response.keywordResults()).isEmpty();
        verifyNoInteractions(newsDiscoveryProvider, persistenceService, postProcessingService);
    }

    @Test
    void processesEnabledKeywordsAndIsolatesOneProviderFailure() {
        Watchlist watchlist = watchlist(1L, "Industry Test", true);
        Keyword battery = keyword(watchlist, 11L, "battery energy storage", true);
        keyword(watchlist, 12L, "disabled keyword", false);
        keyword(watchlist, 13L, "AI data center", true);
        when(watchlistRepository.findWithKeywordsById(1L)).thenReturn(Optional.of(watchlist));

        DiscoveredArticle relevant = article(
                "New battery energy storage facility",
                "https://example.com/relevant#tracking"
        );
        DiscoveredArticle noisy = article(
                "Battery data report",
                "https://example.com/noisy"
        );
        when(newsDiscoveryProvider.discover(new NewsDiscoveryQuery(
                "battery energy storage",
                Instant.parse("2026-08-25T00:00:00Z"),
                Instant.parse("2026-08-26T23:59:59.999999999Z"),
                7
        ))).thenReturn(List.of(relevant, noisy));
        when(newsDiscoveryProvider.discover(new NewsDiscoveryQuery(
                "AI data center",
                Instant.parse("2026-08-25T00:00:00Z"),
                Instant.parse("2026-08-26T23:59:59.999999999Z"),
                7
        ))).thenThrow(new NewsDiscoveryException("gnews was rate limited"));
        Article savedArticle = article(101L);
        when(persistenceService.ingestAndMatch(
                relevant,
                "https://example.com/relevant",
                battery.getId()
        )).thenReturn(new WatchlistDiscoveryPersistenceResult(
                WatchlistDiscoveryPersistenceResult.Status.SAVED,
                savedArticle,
                true
        ));
        when(postProcessingService.process(savedArticle)).thenReturn(successfulPostProcessing());

        WatchlistDiscoveryRunResponse response = service.run(request(
                1L,
                LocalDate.parse("2026-08-25"),
                LocalDate.parse("2026-08-26"),
                7
        ));

        assertThat(response.keywordsProcessed()).isEqualTo(1);
        assertThat(response.keywordsFailed()).isEqualTo(1);
        assertThat(response.discovered()).isEqualTo(2);
        assertThat(response.relevanceRejected()).isEqualTo(1);
        assertThat(response.saved()).isEqualTo(1);
        assertThat(response.duplicates()).isZero();
        assertThat(response.keywordMatchesCreated()).isEqualTo(1);
        assertThat(response.postProcessingAttempted()).isEqualTo(1);
        assertThat(response.metadataTranslationSucceeded()).isEqualTo(1);
        assertThat(response.contentExtractionSucceeded()).isEqualTo(1);
        assertThat(response.contentTranslationSucceeded()).isEqualTo(1);
        assertThat(response.failedKeywords()).containsExactly(
                new WatchlistDiscoveryKeywordFailure(
                        13L,
                        "AI data center",
                        "gnews was rate limited"
                )
        );
        assertThat(response.keywordResults()).hasSize(2);
        verify(newsDiscoveryProvider, times(2)).discover(any(NewsDiscoveryQuery.class));
    }

    @Test
    void sameArticleAcrossKeywordsIsIngestedOnceButMatchedToBoth() {
        Watchlist watchlist = watchlist(1L, "Infrastructure", true);
        Keyword nvidia = keyword(watchlist, 11L, "NVIDIA", true);
        Keyword voltage = keyword(watchlist, 12L, "800VDC", true);
        when(watchlistRepository.findWithKeywordsById(1L)).thenReturn(Optional.of(watchlist));

        DiscoveredArticle article = new DiscoveredArticle(
                "NVIDIA unveils 800VDC architecture",
                "https://example.com/shared#first",
                null,
                "Example",
                null,
                "en"
        );
        when(newsDiscoveryProvider.discover(any(NewsDiscoveryQuery.class)))
                .thenReturn(List.of(article));
        Article savedArticle = article(101L);
        when(persistenceService.ingestAndMatch(
                article,
                "https://example.com/shared",
                nvidia.getId()
        )).thenReturn(new WatchlistDiscoveryPersistenceResult(
                WatchlistDiscoveryPersistenceResult.Status.SAVED,
                savedArticle,
                true
        ));
        when(persistenceService.matchExistingArticle(101L, voltage.getId())).thenReturn(true);
        when(postProcessingService.process(savedArticle)).thenReturn(successfulPostProcessing());

        WatchlistDiscoveryRunResponse response = service.run(request(1L, null, null, 10));

        assertThat(response.saved()).isEqualTo(1);
        assertThat(response.duplicates()).isEqualTo(1);
        assertThat(response.keywordMatchesCreated()).isEqualTo(2);
        assertThat(response.postProcessingAttempted()).isEqualTo(1);
        verify(persistenceService).ingestAndMatch(
                article,
                "https://example.com/shared",
                nvidia.getId()
        );
        verify(persistenceService).matchExistingArticle(101L, voltage.getId());
        verify(postProcessingService).process(savedArticle);
    }

    @Test
    void reportsExistingDatabaseArticleAndUnsupportedOrInvalidCandidates() {
        Watchlist watchlist = watchlist(1L, "Mixed", true);
        Keyword keyword = keyword(watchlist, 11L, "NVIDIA", true);
        when(watchlistRepository.findWithKeywordsById(1L)).thenReturn(Optional.of(watchlist));
        DiscoveredArticle duplicate = article(
                "NVIDIA update",
                "https://known.example/article"
        );
        DiscoveredArticle unsupported = article(
                "NVIDIA French report",
                "https://new.example/fr"
        );
        DiscoveredArticle invalid = article("NVIDIA invalid URL", "not-a-url");
        when(newsDiscoveryProvider.discover(any(NewsDiscoveryQuery.class)))
                .thenReturn(List.of(duplicate, unsupported, invalid));
        when(persistenceService.ingestAndMatch(
                duplicate,
                duplicate.url(),
                keyword.getId()
        )).thenReturn(new WatchlistDiscoveryPersistenceResult(
                WatchlistDiscoveryPersistenceResult.Status.DUPLICATE,
                article(100L),
                true
        ));
        when(persistenceService.ingestAndMatch(
                unsupported,
                unsupported.url(),
                keyword.getId()
        )).thenReturn(WatchlistDiscoveryPersistenceResult.unsupportedLanguage());

        WatchlistDiscoveryRunResponse response = service.run(request(1L, null, null, 10));

        assertThat(response.discovered()).isEqualTo(3);
        assertThat(response.duplicates()).isEqualTo(1);
        assertThat(response.keywordMatchesCreated()).isEqualTo(1);
        assertThat(response.skippedUnsupportedLanguage()).isEqualTo(1);
        assertThat(response.skippedInvalidUrl()).isEqualTo(1);
        assertThat(response.postProcessingAttempted()).isZero();
        verifyNoInteractions(postProcessingService);
    }

    @Test
    void articlePostProcessingFailureDoesNotStopNextArticleOrFailKeyword() {
        Watchlist watchlist = watchlist(1L, "NVIDIA Test", true);
        Keyword keyword = keyword(watchlist, 11L, "NVIDIA", true);
        when(watchlistRepository.findWithKeywordsById(1L)).thenReturn(Optional.of(watchlist));
        DiscoveredArticle first = article("NVIDIA first", "https://example.com/first");
        DiscoveredArticle second = article("NVIDIA second", "https://example.com/second");
        when(newsDiscoveryProvider.discover(any(NewsDiscoveryQuery.class)))
                .thenReturn(List.of(first, second));
        Article firstSaved = article(201L);
        Article secondSaved = article(202L);
        when(persistenceService.ingestAndMatch(first, first.url(), keyword.getId()))
                .thenReturn(new WatchlistDiscoveryPersistenceResult(
                        WatchlistDiscoveryPersistenceResult.Status.SAVED,
                        firstSaved,
                        true
                ));
        when(persistenceService.ingestAndMatch(second, second.url(), keyword.getId()))
                .thenReturn(new WatchlistDiscoveryPersistenceResult(
                        WatchlistDiscoveryPersistenceResult.Status.SAVED,
                        secondSaved,
                        true
                ));
        when(postProcessingService.process(firstSaved)).thenReturn(
                new ArticlePostProcessingResult(false, true, false, true, false, false)
        );
        when(postProcessingService.process(secondSaved)).thenReturn(successfulPostProcessing());

        WatchlistDiscoveryRunResponse response = service.run(request(1L, null, null, 10));

        assertThat(response.saved()).isEqualTo(2);
        assertThat(response.keywordsProcessed()).isEqualTo(1);
        assertThat(response.keywordsFailed()).isZero();
        assertThat(response.postProcessingAttempted()).isEqualTo(2);
        assertThat(response.metadataTranslationSucceeded()).isEqualTo(1);
        assertThat(response.metadataTranslationFailed()).isEqualTo(1);
        assertThat(response.contentExtractionSucceeded()).isEqualTo(1);
        assertThat(response.contentExtractionFailed()).isEqualTo(1);
        assertThat(response.contentTranslationSucceeded()).isEqualTo(1);
        assertThat(response.contentTranslationFailed()).isZero();
        verify(postProcessingService).process(firstSaved);
        verify(postProcessingService).process(secondSaved);
    }

    @Test
    void rejectsInvalidDateRangeAndUnavailableProviderClearly() {
        assertThatThrownBy(() -> service.run(request(
                1L,
                LocalDate.parse("2026-08-27"),
                LocalDate.parse("2026-08-26"),
                10
        ))).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Discovery start date must not be after end date");
        verify(watchlistRepository, never()).findWithKeywordsById(any());

        Watchlist watchlist = watchlist(1L, "Enabled", true);
        keyword(watchlist, 11L, "NVIDIA", true);
        when(watchlistRepository.findWithKeywordsById(1L)).thenReturn(Optional.of(watchlist));
        assertThatThrownBy(() -> service(Optional.empty()).run(request(1L, null, null, 10)))
                .isInstanceOf(WatchlistDiscoveryProviderUnavailableException.class)
                .hasMessage("News discovery provider is not configured.");
    }

    private WatchlistDiscoveryService service(Optional<NewsDiscoveryService> provider) {
        return new WatchlistDiscoveryService(
                watchlistRepository,
                provider,
                new NewsDiscoveryQueryFactory(Clock.fixed(NOW, ZoneOffset.UTC)),
                new DiscoveryKeywordMatcher(),
                new DiscoveryUrlNormalizer(),
                persistenceService,
                postProcessingService
        );
    }

    private WatchlistDiscoveryRunRequest request(
            Long watchlistId,
            LocalDate from,
            LocalDate to,
            Integer limit
    ) {
        return new WatchlistDiscoveryRunRequest(watchlistId, from, to, limit);
    }

    private Watchlist watchlist(Long id, String name, boolean enabled) {
        Watchlist watchlist = new Watchlist(name);
        watchlist.setEnabled(enabled);
        ReflectionTestUtils.setField(watchlist, "id", id);
        return watchlist;
    }

    private Keyword keyword(Watchlist watchlist, Long id, String text, boolean enabled) {
        Keyword keyword = watchlist.addKeyword(text);
        keyword.setEnabled(enabled);
        ReflectionTestUtils.setField(keyword, "id", id);
        return keyword;
    }

    private DiscoveredArticle article(String title, String url) {
        return new DiscoveredArticle(title, url, null, "Example", null, "en");
    }

    private Article article(Long id) {
        Source source = new Source(
                "Example",
                "https://example.com",
                SourceType.WEBSITE,
                SourcePriority.MEDIUM,
                SourceLanguage.EN
        );
        Article article = new Article(
                "Persisted article",
                "https://example.com/" + id,
                source,
                NOW
        );
        ReflectionTestUtils.setField(article, "id", id);
        return article;
    }

    private ArticlePostProcessingResult successfulPostProcessing() {
        return new ArticlePostProcessingResult(true, false, true, false, true, false);
    }
}
