package com.carya.energynews.dailybrief;

import com.carya.energynews.article.Article;
import com.carya.energynews.article.ArticleRepository;
import com.carya.energynews.translation.ArticleTranslation;
import com.carya.energynews.translation.ArticleTranslationRepository;
import com.carya.energynews.translation.TranslationLanguage;
import com.carya.energynews.translation.TranslationStatus;
import com.carya.energynews.watchlist.Watchlist;
import com.carya.energynews.watchlist.WatchlistNotFoundException;
import com.carya.energynews.watchlist.WatchlistRepository;
import com.carya.energynews.watchlistdiscovery.ArticleKeywordMatchRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class DailyBriefService {

    private final DailyBriefRepository dailyBriefRepository;
    private final DailyBriefItemRepository dailyBriefItemRepository;
    private final WatchlistRepository watchlistRepository;
    private final ArticleRepository articleRepository;
    private final ArticleTranslationRepository translationRepository;
    private final ArticleKeywordMatchRepository matchRepository;
    private final DailyBriefProperties properties;
    private final Clock clock;

    public DailyBriefService(
            DailyBriefRepository dailyBriefRepository,
            DailyBriefItemRepository dailyBriefItemRepository,
            WatchlistRepository watchlistRepository,
            ArticleRepository articleRepository,
            ArticleTranslationRepository translationRepository,
            ArticleKeywordMatchRepository matchRepository,
            DailyBriefProperties properties,
            Clock clock
    ) {
        this.dailyBriefRepository = dailyBriefRepository;
        this.dailyBriefItemRepository = dailyBriefItemRepository;
        this.watchlistRepository = watchlistRepository;
        this.articleRepository = articleRepository;
        this.translationRepository = translationRepository;
        this.matchRepository = matchRepository;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public DailyBriefResponse generate(GenerateDailyBriefRequest request) {
        Watchlist watchlist = watchlistRepository.findById(request.watchlistId())
                .orElseThrow(() -> new WatchlistNotFoundException(request.watchlistId()));
        if (!watchlist.isEnabled()) {
            throw new DailyBriefWatchlistDisabledException(watchlist.getId());
        }

        int maxItems = request.maxItems() == null ? properties.maxItems() : request.maxItems();
        validateMaxItems(maxItems);

        ZoneId zone = properties.zoneId();
        LocalDate briefDate = request.date() == null
                ? LocalDate.ofInstant(clock.instant(), zone)
                : request.date();
        Instant windowStart = briefDate.atStartOfDay(zone).toInstant();
        Instant windowEnd = briefDate.plusDays(1).atStartOfDay(zone).toInstant();

        Page<DailyBriefCandidate> candidates = matchRepository.findDailyBriefCandidates(
                watchlist.getId(),
                windowStart,
                windowEnd,
                PageRequest.of(0, maxItems)
        );

        int candidateCount = Math.toIntExact(candidates.getTotalElements());
        DailyBrief brief = dailyBriefRepository
                .findByWatchlistIdAndBriefDate(watchlist.getId(), briefDate)
                .orElseGet(() -> new DailyBrief(
                        watchlist,
                        briefDate,
                        properties.zone(),
                        windowStart,
                        windowEnd,
                        candidateCount
                ));
        brief.updateSnapshot(properties.zone(), windowStart, windowEnd, candidateCount);
        brief = dailyBriefRepository.saveAndFlush(brief);

        dailyBriefItemRepository.deleteAllByDailyBriefId(brief.getId());

        List<Long> articleIds = candidates.stream()
                .map(DailyBriefCandidate::articleId)
                .toList();
        Map<Long, Article> articlesById = articleIds.isEmpty()
                ? Map.of()
                : articleRepository.findAllByIdIn(articleIds).stream()
                        .collect(Collectors.toMap(Article::getId, Function.identity()));

        List<DailyBriefItem> items = new ArrayList<>(articleIds.size());
        for (int index = 0; index < articleIds.size(); index++) {
            Long articleId = articleIds.get(index);
            Article article = articlesById.get(articleId);
            if (article == null) {
                throw new IllegalStateException("Daily brief candidate article " + articleId + " was not found");
            }
            items.add(new DailyBriefItem(brief, article, index + 1));
        }
        items = dailyBriefItemRepository.saveAllAndFlush(items);

        return toResponse(brief, items);
    }

    @Transactional(readOnly = true)
    public DailyBriefResponse getById(Long id) {
        DailyBrief brief = dailyBriefRepository.findById(id)
                .orElseThrow(() -> new DailyBriefNotFoundException(id));
        return toResponse(
                brief,
                dailyBriefItemRepository.findAllByDailyBriefIdOrderByRankAsc(id)
        );
    }

    @Transactional(readOnly = true)
    public DailyBriefResponse getByWatchlistAndDate(Long watchlistId, LocalDate date) {
        DailyBrief brief = dailyBriefRepository.findByWatchlistIdAndBriefDate(watchlistId, date)
                .orElseThrow(() -> new DailyBriefNotFoundException(watchlistId, date));
        return toResponse(
                brief,
                dailyBriefItemRepository.findAllByDailyBriefIdOrderByRankAsc(brief.getId())
        );
    }

    private DailyBriefResponse toResponse(DailyBrief brief, List<DailyBriefItem> items) {
        List<Long> articleIds = items.stream()
                .map(item -> item.getArticle().getId())
                .toList();

        Map<Long, ArticleTranslation> translationsByArticleId = articleIds.isEmpty()
                ? Map.of()
                : translationRepository.findAllByArticleIdInAndLanguageAndStatus(
                                articleIds,
                                TranslationLanguage.ZH_CN,
                                TranslationStatus.SUCCESS
                        ).stream()
                        .collect(Collectors.toMap(
                                translation -> translation.getArticle().getId(),
                                Function.identity()
                        ));

        Map<Long, List<String>> keywordsByArticleId = new LinkedHashMap<>();
        if (!articleIds.isEmpty()) {
            matchRepository.findDailyBriefMatchedKeywords(
                    brief.getWatchlist().getId(),
                    articleIds
            ).forEach(match -> keywordsByArticleId
                    .computeIfAbsent(match.articleId(), ignored -> new ArrayList<>())
                    .add(match.keyword()));
        }

        List<DailyBriefItemResponse> itemResponses = items.stream()
                .map(item -> toResponse(
                        item,
                        item.getArticle(),
                        translationsByArticleId.get(item.getArticle().getId()),
                        keywordsByArticleId.getOrDefault(item.getArticle().getId(), List.of())
                ))
                .toList();

        return new DailyBriefResponse(
                brief.getId(),
                brief.getWatchlist().getId(),
                brief.getWatchlist().getName(),
                brief.getBriefDate(),
                brief.getZone(),
                brief.getWindowStart(),
                brief.getWindowEnd(),
                brief.getCandidateCount(),
                itemResponses.size(),
                brief.getCreatedAt(),
                brief.getUpdatedAt(),
                itemResponses
        );
    }

    private static DailyBriefItemResponse toResponse(
            DailyBriefItem item,
            Article article,
            ArticleTranslation translation,
            List<String> matchedKeywords
    ) {
        String title = translation == null || isBlank(translation.getTitle())
                ? article.getTitle()
                : translation.getTitle();
        String description = translation == null || isBlank(translation.getDescription())
                ? article.getDescription()
                : translation.getDescription();
        Instant effectiveTime = article.getPublishedAt() == null
                ? article.getCollectedAt()
                : article.getPublishedAt();

        return new DailyBriefItemResponse(
                item.getRank(),
                article.getId(),
                title,
                description,
                article.getUrl(),
                article.getSource().getName(),
                article.getPublishedAt(),
                effectiveTime,
                matchedKeywords.size(),
                List.copyOf(matchedKeywords)
        );
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static void validateMaxItems(int maxItems) {
        if (maxItems < 1 || maxItems > 20) {
            throw new IllegalArgumentException("Daily brief max items must be between 1 and 20");
        }
    }
}
