package com.carya.energynews.watchlistdiscovery;

import com.carya.energynews.article.ArticlePostProcessingResult;
import com.carya.energynews.article.ArticlePostProcessingService;
import com.carya.energynews.discovery.DiscoveredArticle;
import com.carya.energynews.discovery.NewsDiscoveryException;
import com.carya.energynews.discovery.NewsDiscoveryQuery;
import com.carya.energynews.discovery.NewsDiscoveryQueryFactory;
import com.carya.energynews.discovery.NewsDiscoveryService;
import com.carya.energynews.watchlist.Keyword;
import com.carya.energynews.watchlist.Watchlist;
import com.carya.energynews.watchlist.WatchlistNotFoundException;
import com.carya.energynews.watchlist.WatchlistRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

@Service
public class WatchlistDiscoveryService {

    private static final String UNEXPECTED_FAILURE = "Unexpected processing failure";
    private static final Comparator<Keyword> KEYWORD_ORDER = Comparator
            .comparing(Keyword::getId, Comparator.nullsLast(Long::compareTo));

    private final WatchlistRepository watchlistRepository;
    private final Optional<NewsDiscoveryService> discoveryService;
    private final NewsDiscoveryQueryFactory queryFactory;
    private final DiscoveryKeywordMatcher keywordMatcher;
    private final DiscoveryUrlNormalizer urlNormalizer;
    private final WatchlistDiscoveryPersistenceService persistenceService;
    private final ArticlePostProcessingService postProcessingService;

    public WatchlistDiscoveryService(
            WatchlistRepository watchlistRepository,
            Optional<NewsDiscoveryService> discoveryService,
            NewsDiscoveryQueryFactory queryFactory,
            DiscoveryKeywordMatcher keywordMatcher,
            DiscoveryUrlNormalizer urlNormalizer,
            WatchlistDiscoveryPersistenceService persistenceService,
            ArticlePostProcessingService postProcessingService
    ) {
        this.watchlistRepository = watchlistRepository;
        this.discoveryService = discoveryService;
        this.queryFactory = queryFactory;
        this.keywordMatcher = keywordMatcher;
        this.urlNormalizer = urlNormalizer;
        this.persistenceService = persistenceService;
        this.postProcessingService = postProcessingService;
    }

    public WatchlistDiscoveryRunResponse run(WatchlistDiscoveryRunRequest request) {
        validateDateRange(request);
        Watchlist watchlist = watchlistRepository.findWithKeywordsById(request.watchlistId())
                .orElseThrow(() -> new WatchlistNotFoundException(request.watchlistId()));
        if (!watchlist.isEnabled()) {
            throw new WatchlistDisabledException(watchlist.getId());
        }

        return execute(
                watchlist,
                keyword -> queryFactory.create(
                        keyword.getKeyword(),
                        request.from(),
                        request.to(),
                        request.limitPerKeyword()
                ),
                DiscoveryRequestPacer.noDelay(),
                new DiscoveryRequestBudget(Integer.MAX_VALUE)
        ).response();
    }

    public boolean isProviderAvailable() {
        return discoveryService.isPresent();
    }

    WatchlistDiscoveryExecutionResult runScheduled(
            Long watchlistId,
            Instant from,
            Instant to,
            int limitPerKeyword,
            DiscoveryRequestPacer pacer,
            DiscoveryRequestBudget requestBudget
    ) {
        Objects.requireNonNull(from, "Scheduled discovery start time is required");
        Objects.requireNonNull(to, "Scheduled discovery end time is required");
        Objects.requireNonNull(pacer, "Discovery request pacer is required");
        Objects.requireNonNull(requestBudget, "Discovery request budget is required");
        if (from.isAfter(to)) {
            throw new IllegalArgumentException(
                    "Scheduled discovery start time must not be after end time"
            );
        }

        Watchlist watchlist = watchlistRepository.findWithKeywordsById(watchlistId)
                .orElseThrow(() -> new WatchlistNotFoundException(watchlistId));
        if (!watchlist.isEnabled()) {
            throw new WatchlistDisabledException(watchlist.getId());
        }

        return execute(
                watchlist,
                keyword -> queryFactory.create(
                        keyword.getKeyword(),
                        from,
                        to,
                        limitPerKeyword
                ),
                pacer,
                requestBudget
        );
    }

    private WatchlistDiscoveryExecutionResult execute(
            Watchlist watchlist,
            Function<Keyword, NewsDiscoveryQuery> queryFactory,
            DiscoveryRequestPacer pacer,
            DiscoveryRequestBudget requestBudget
    ) {

        List<Keyword> keywords = watchlist.getKeywords().stream()
                .filter(Keyword::isEnabled)
                .sorted(KEYWORD_ORDER)
                .toList();
        if (keywords.isEmpty()) {
            return new WatchlistDiscoveryExecutionResult(emptyResponse(watchlist), 0);
        }
        if (requestBudget.remaining() == 0) {
            return new WatchlistDiscoveryExecutionResult(
                    emptyResponse(watchlist),
                    keywords.size()
            );
        }

        NewsDiscoveryService service = discoveryService.orElseThrow(
                WatchlistDiscoveryProviderUnavailableException::new
        );
        RunCounters totals = new RunCounters();
        Map<String, Long> articleIdsByUrl = new HashMap<>();
        List<WatchlistDiscoveryKeywordFailure> failures = new ArrayList<>();
        List<WatchlistDiscoveryKeywordResult> keywordResults = new ArrayList<>();
        int keywordsSkippedByRequestLimit = 0;

        for (int index = 0; index < keywords.size(); index++) {
            Keyword keyword = keywords.get(index);
            NewsDiscoveryQuery query = queryFactory.apply(keyword);
            if (!requestBudget.tryAcquire()) {
                keywordsSkippedByRequestLimit = keywords.size() - index;
                break;
            }

            KeywordCounters counters = new KeywordCounters(keyword);
            try {
                pacer.awaitNextRequest();
                List<DiscoveredArticle> discovered = service.discover(query);
                counters.discovered = discovered.size();
                for (DiscoveredArticle article : discovered) {
                    processCandidate(
                            keyword,
                            article,
                            counters,
                            articleIdsByUrl
                    );
                }
                totals.keywordsProcessed++;
            } catch (NewsDiscoveryException exception) {
                totals.keywordsFailed++;
                counters.failure = exception.getMessage();
                failures.add(new WatchlistDiscoveryKeywordFailure(
                        keyword.getId(),
                        keyword.getKeyword(),
                        exception.getMessage()
                ));
            } catch (RuntimeException exception) {
                totals.keywordsFailed++;
                counters.failure = UNEXPECTED_FAILURE;
                failures.add(new WatchlistDiscoveryKeywordFailure(
                        keyword.getId(),
                        keyword.getKeyword(),
                        UNEXPECTED_FAILURE
                ));
                totals.add(counters);
                keywordResults.add(counters.toResult());
                throw new WatchlistDiscoveryExecutionException(
                        exception,
                        new WatchlistDiscoveryExecutionResult(
                                totals.toResponse(watchlist, failures, keywordResults),
                                0
                        )
                );
            }
            totals.add(counters);
            keywordResults.add(counters.toResult());
        }

        return new WatchlistDiscoveryExecutionResult(
                totals.toResponse(watchlist, failures, keywordResults),
                keywordsSkippedByRequestLimit
        );
    }

    private void processCandidate(
            Keyword keyword,
            DiscoveredArticle article,
            KeywordCounters counters,
            Map<String, Long> articleIdsByUrl
    ) {
        if (!keywordMatcher.matches(keyword.getKeyword(), article)) {
            counters.relevanceRejected++;
            return;
        }

        Optional<String> normalizedUrl = urlNormalizer.normalize(article.url());
        if (normalizedUrl.isEmpty()) {
            counters.skippedInvalidUrl++;
            return;
        }

        Long existingRunArticleId = articleIdsByUrl.get(normalizedUrl.get());
        if (existingRunArticleId != null) {
            counters.duplicates++;
            countMatch(
                    persistenceService.matchExistingArticle(
                            existingRunArticleId,
                            keyword.getId()
                    ),
                    counters
            );
            return;
        }

        WatchlistDiscoveryPersistenceResult result = persistenceService.ingestAndMatch(
                article,
                normalizedUrl.get(),
                keyword.getId()
        );
        if (result.status()
                == WatchlistDiscoveryPersistenceResult.Status.SKIPPED_UNSUPPORTED_LANGUAGE) {
            counters.skippedUnsupportedLanguage++;
            return;
        }

        articleIdsByUrl.put(normalizedUrl.get(), result.articleId());
        countMatch(result.keywordMatchCreated(), counters);
        switch (result.status()) {
            case SAVED -> {
                counters.saved++;
                counters.postProcessingAttempted++;
                counters.addPostProcessing(postProcessingService.process(result.article()));
            }
            case DUPLICATE -> counters.duplicates++;
            case SKIPPED_UNSUPPORTED_LANGUAGE -> throw new IllegalStateException(
                    "Unsupported language result must be handled before ingestion counters"
            );
        }
    }

    private void countMatch(boolean created, KeywordCounters counters) {
        if (created) {
            counters.keywordMatchesCreated++;
        } else {
            counters.keywordMatchesExisting++;
        }
    }

    private void validateDateRange(WatchlistDiscoveryRunRequest request) {
        if (request.from() != null
                && request.to() != null
                && request.from().isAfter(request.to())) {
            throw new IllegalArgumentException("Discovery start date must not be after end date");
        }
    }

    private WatchlistDiscoveryRunResponse emptyResponse(Watchlist watchlist) {
        return new WatchlistDiscoveryRunResponse(
                watchlist.getId(),
                watchlist.getName(),
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                List.of(),
                List.of()
        );
    }

    private static final class KeywordCounters {

        private final Keyword keyword;
        private int discovered;
        private int relevanceRejected;
        private int saved;
        private int duplicates;
        private int keywordMatchesCreated;
        private int keywordMatchesExisting;
        private int skippedUnsupportedLanguage;
        private int skippedInvalidUrl;
        private int postProcessingAttempted;
        private int metadataTranslationSucceeded;
        private int metadataTranslationFailed;
        private int contentExtractionSucceeded;
        private int contentExtractionFailed;
        private int contentTranslationSucceeded;
        private int contentTranslationFailed;
        private String failure;

        private KeywordCounters(Keyword keyword) {
            this.keyword = keyword;
        }

        private void addPostProcessing(ArticlePostProcessingResult result) {
            metadataTranslationSucceeded += result.metadataTranslationSucceeded() ? 1 : 0;
            metadataTranslationFailed += result.metadataTranslationFailed() ? 1 : 0;
            contentExtractionSucceeded += result.contentExtractionSucceeded() ? 1 : 0;
            contentExtractionFailed += result.contentExtractionFailed() ? 1 : 0;
            contentTranslationSucceeded += result.contentTranslationSucceeded() ? 1 : 0;
            contentTranslationFailed += result.contentTranslationFailed() ? 1 : 0;
        }

        private WatchlistDiscoveryKeywordResult toResult() {
            return new WatchlistDiscoveryKeywordResult(
                    keyword.getId(),
                    keyword.getKeyword(),
                    discovered,
                    relevanceRejected,
                    saved,
                    duplicates,
                    keywordMatchesCreated,
                    keywordMatchesExisting,
                    skippedUnsupportedLanguage,
                    skippedInvalidUrl,
                    failure
            );
        }
    }

    private static final class RunCounters {

        private int keywordsProcessed;
        private int keywordsFailed;
        private int discovered;
        private int relevanceRejected;
        private int saved;
        private int duplicates;
        private int keywordMatchesCreated;
        private int keywordMatchesExisting;
        private int skippedUnsupportedLanguage;
        private int skippedInvalidUrl;
        private int postProcessingAttempted;
        private int metadataTranslationSucceeded;
        private int metadataTranslationFailed;
        private int contentExtractionSucceeded;
        private int contentExtractionFailed;
        private int contentTranslationSucceeded;
        private int contentTranslationFailed;

        private void add(KeywordCounters counters) {
            discovered += counters.discovered;
            relevanceRejected += counters.relevanceRejected;
            saved += counters.saved;
            duplicates += counters.duplicates;
            keywordMatchesCreated += counters.keywordMatchesCreated;
            keywordMatchesExisting += counters.keywordMatchesExisting;
            skippedUnsupportedLanguage += counters.skippedUnsupportedLanguage;
            skippedInvalidUrl += counters.skippedInvalidUrl;
            postProcessingAttempted += counters.postProcessingAttempted;
            metadataTranslationSucceeded += counters.metadataTranslationSucceeded;
            metadataTranslationFailed += counters.metadataTranslationFailed;
            contentExtractionSucceeded += counters.contentExtractionSucceeded;
            contentExtractionFailed += counters.contentExtractionFailed;
            contentTranslationSucceeded += counters.contentTranslationSucceeded;
            contentTranslationFailed += counters.contentTranslationFailed;
        }

        private WatchlistDiscoveryRunResponse toResponse(
                Watchlist watchlist,
                List<WatchlistDiscoveryKeywordFailure> failures,
                List<WatchlistDiscoveryKeywordResult> keywordResults
        ) {
            return new WatchlistDiscoveryRunResponse(
                    watchlist.getId(),
                    watchlist.getName(),
                    keywordsProcessed,
                    keywordsFailed,
                    discovered,
                    relevanceRejected,
                    saved,
                    duplicates,
                    keywordMatchesCreated,
                    keywordMatchesExisting,
                    skippedUnsupportedLanguage,
                    skippedInvalidUrl,
                    postProcessingAttempted,
                    metadataTranslationSucceeded,
                    metadataTranslationFailed,
                    contentExtractionSucceeded,
                    contentExtractionFailed,
                    contentTranslationSucceeded,
                    contentTranslationFailed,
                    List.copyOf(failures),
                    List.copyOf(keywordResults)
            );
        }
    }
}
