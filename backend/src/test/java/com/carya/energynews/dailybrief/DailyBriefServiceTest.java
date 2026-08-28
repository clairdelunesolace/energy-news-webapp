package com.carya.energynews.dailybrief;

import com.carya.energynews.article.Article;
import com.carya.energynews.article.ArticleRepository;
import com.carya.energynews.source.Source;
import com.carya.energynews.source.SourcePriority;
import com.carya.energynews.source.SourceRepository;
import com.carya.energynews.source.SourceType;
import com.carya.energynews.translation.ArticleTranslation;
import com.carya.energynews.translation.ArticleTranslationRepository;
import com.carya.energynews.translation.TranslationLanguage;
import com.carya.energynews.translation.TranslationService;
import com.carya.energynews.translation.TranslationStatus;
import com.carya.energynews.watchlist.Keyword;
import com.carya.energynews.watchlist.Watchlist;
import com.carya.energynews.watchlist.WatchlistNotFoundException;
import com.carya.energynews.watchlist.WatchlistRepository;
import com.carya.energynews.watchlistdiscovery.ArticleKeywordMatch;
import com.carya.energynews.watchlistdiscovery.ArticleKeywordMatchRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;

@SpringBootTest(properties = {
        "app.security.admin.password=test-password",
        "app.discovery.provider=none",
        "app.discovery.scheduler.enabled=false",
        "app.daily-brief.zone=Asia/Shanghai",
        "app.daily-brief.max-items=2",
        "spring.datasource.url=jdbc:h2:mem:daily-brief-service;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.open-in-view=false"
})
@Import(DailyBriefServiceTest.FixedClockConfiguration.class)
class DailyBriefServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-28T04:00:00Z");
    private static final LocalDate BRIEF_DATE = LocalDate.parse("2026-08-28");
    private static final Instant WINDOW_START = Instant.parse("2026-08-27T16:00:00Z");
    private static final Instant WINDOW_END = Instant.parse("2026-08-28T16:00:00Z");

    @Autowired
    private DailyBriefService dailyBriefService;

    @Autowired
    private DailyBriefRepository dailyBriefRepository;

    @Autowired
    private DailyBriefItemRepository dailyBriefItemRepository;

    @Autowired
    private WatchlistRepository watchlistRepository;

    @Autowired
    private SourceRepository sourceRepository;

    @Autowired
    private ArticleRepository articleRepository;

    @Autowired
    private ArticleTranslationRepository translationRepository;

    @Autowired
    private ArticleKeywordMatchRepository matchRepository;

    @MockitoBean
    private TranslationService translationService;

    @BeforeEach
    void clearDatabase() {
        dailyBriefItemRepository.deleteAll();
        dailyBriefRepository.deleteAll();
        translationRepository.deleteAll();
        matchRepository.deleteAll();
        articleRepository.deleteAll();
        watchlistRepository.deleteAll();
        sourceRepository.deleteAll();
    }

    @Test
    void generatesDeterministicDeduplicatedChineseFirstSnapshotFromHistoricalMatches() {
        Source source = saveSource();
        Watchlist watchlist = new Watchlist("Storage");
        Keyword battery = watchlist.addKeyword("battery storage");
        Keyword grid = watchlist.addKeyword("grid battery");
        grid.setEnabled(false);
        watchlistRepository.saveAndFlush(watchlist);

        Watchlist otherWatchlist = new Watchlist("Other");
        Keyword otherKeyword = otherWatchlist.addKeyword("unrelated");
        watchlistRepository.saveAndFlush(otherWatchlist);

        Instant tieTime = WINDOW_START.plusSeconds(7_200);
        Article lowerIdTie = saveArticle(
                source,
                "Lower ID original",
                "Lower description",
                null,
                tieTime
        );
        Article higherIdTie = saveArticle(
                source,
                "Higher ID original",
                "Higher description",
                tieTime,
                WINDOW_START
        );
        Article twoKeywordArticle = saveArticle(
                source,
                "Two keyword original",
                "Original description",
                WINDOW_START.plusSeconds(3_600),
                WINDOW_START
        );
        Article otherOnly = saveArticle(
                source,
                "Other watchlist",
                "Other description",
                tieTime.plusSeconds(1),
                WINDOW_START
        );

        matchRepository.saveAllAndFlush(List.of(
                new ArticleKeywordMatch(lowerIdTie, battery),
                new ArticleKeywordMatch(higherIdTie, battery),
                new ArticleKeywordMatch(higherIdTie, otherKeyword),
                new ArticleKeywordMatch(twoKeywordArticle, battery),
                new ArticleKeywordMatch(twoKeywordArticle, grid),
                new ArticleKeywordMatch(twoKeywordArticle, otherKeyword),
                new ArticleKeywordMatch(otherOnly, otherKeyword)
        ));

        ArticleTranslation successful = new ArticleTranslation(
                twoKeywordArticle,
                TranslationLanguage.ZH_CN
        );
        successful.setTitle("双关键词中文标题");
        successful.setDescription("   ");
        successful.setStatus(TranslationStatus.SUCCESS);
        ArticleTranslation failed = new ArticleTranslation(
                higherIdTie,
                TranslationLanguage.ZH_CN
        );
        failed.setTitle("不应使用");
        failed.setDescription("不应使用");
        failed.setStatus(TranslationStatus.FAILED);
        translationRepository.saveAllAndFlush(List.of(successful, failed));

        DailyBriefResponse response = dailyBriefService.generate(
                new GenerateDailyBriefRequest(watchlist.getId(), BRIEF_DATE, 10)
        );

        assertThat(response.briefDate()).isEqualTo(BRIEF_DATE);
        assertThat(response.zone()).isEqualTo("Asia/Shanghai");
        assertThat(response.windowStart()).isEqualTo(WINDOW_START);
        assertThat(response.windowEnd()).isEqualTo(WINDOW_END);
        assertThat(response.candidateCount()).isEqualTo(3);
        assertThat(response.itemCount()).isEqualTo(3);
        assertThat(response.items())
                .extracting(DailyBriefItemResponse::articleId)
                .containsExactly(
                        twoKeywordArticle.getId(),
                        higherIdTie.getId(),
                        lowerIdTie.getId()
                );

        DailyBriefItemResponse first = response.items().getFirst();
        assertThat(first.rank()).isEqualTo(1);
        assertThat(first.title()).isEqualTo("双关键词中文标题");
        assertThat(first.description()).isEqualTo("Original description");
        assertThat(first.matchingKeywordCount()).isEqualTo(2);
        assertThat(first.matchedKeywords())
                .containsExactly("battery storage", "grid battery");
        assertThat(response.items().get(1).title()).isEqualTo("Higher ID original");
        assertThat(response.items().get(2).publishedAt()).isNull();
        assertThat(response.items().get(2).effectiveTime()).isEqualTo(tieTime);
        assertThat(dailyBriefItemRepository.count()).isEqualTo(3);
        assertThat(dailyBriefRepository.count()).isEqualTo(1);

        DailyBriefResponse byId = dailyBriefService.getById(response.id());
        DailyBriefResponse byLookup = dailyBriefService.getByWatchlistAndDate(
                watchlist.getId(),
                BRIEF_DATE
        );
        assertThat(byId.id()).isEqualTo(response.id());
        assertThat(byId.items()).isEqualTo(response.items());
        assertThat(byLookup.id()).isEqualTo(response.id());
        assertThat(byLookup.items()).isEqualTo(response.items());
        verifyNoInteractions(translationService);
    }

    @Test
    void defaultsDateFromFixedClockUsesHalfOpenWindowAndRegeneratesInPlace() {
        Source source = saveSource();
        Watchlist watchlist = new Watchlist("Boundaries");
        Keyword keyword = watchlist.addKeyword("storage");
        watchlistRepository.saveAndFlush(watchlist);

        Article beforeStart = saveArticle(
                source,
                "Before start",
                null,
                null,
                WINDOW_START.minusSeconds(1)
        );
        Article atStart = saveArticle(source, "At start", null, null, WINDOW_START);
        Article middle = saveArticle(
                source,
                "Middle",
                null,
                WINDOW_START.plusSeconds(3_600),
                WINDOW_START.minusSeconds(10)
        );
        Article beforeEnd = saveArticle(
                source,
                "Before end",
                null,
                null,
                WINDOW_END.minusSeconds(1)
        );
        Article atEnd = saveArticle(source, "At end", null, WINDOW_END, WINDOW_START);
        matchRepository.saveAllAndFlush(List.of(
                new ArticleKeywordMatch(beforeStart, keyword),
                new ArticleKeywordMatch(atStart, keyword),
                new ArticleKeywordMatch(middle, keyword),
                new ArticleKeywordMatch(beforeEnd, keyword),
                new ArticleKeywordMatch(atEnd, keyword)
        ));

        DailyBriefResponse initial = dailyBriefService.generate(
                new GenerateDailyBriefRequest(watchlist.getId(), null, null)
        );

        assertThat(initial.briefDate()).isEqualTo(BRIEF_DATE);
        assertThat(initial.candidateCount()).isEqualTo(3);
        assertThat(initial.items())
                .extracting(DailyBriefItemResponse::articleId)
                .containsExactly(beforeEnd.getId(), middle.getId());

        DailyBriefResponse full = dailyBriefService.generate(
                new GenerateDailyBriefRequest(watchlist.getId(), BRIEF_DATE, 3)
        );
        assertThat(full.id()).isEqualTo(initial.id());
        assertThat(full.items())
                .extracting(DailyBriefItemResponse::articleId)
                .containsExactly(beforeEnd.getId(), middle.getId(), atStart.getId());

        DailyBriefResponse limited = dailyBriefService.generate(
                new GenerateDailyBriefRequest(watchlist.getId(), BRIEF_DATE, 1)
        );
        assertThat(limited.id()).isEqualTo(initial.id());
        assertThat(limited.candidateCount()).isEqualTo(3);
        assertThat(limited.itemCount()).isEqualTo(1);
        assertThat(limited.items().getFirst().articleId()).isEqualTo(beforeEnd.getId());
        assertThat(dailyBriefRepository.count()).isEqualTo(1);
        assertThat(dailyBriefItemRepository.count()).isEqualTo(1);

        matchRepository.deleteAll();
        DailyBriefResponse empty = dailyBriefService.generate(
                new GenerateDailyBriefRequest(watchlist.getId(), BRIEF_DATE, null)
        );
        assertThat(empty.id()).isEqualTo(initial.id());
        assertThat(empty.candidateCount()).isZero();
        assertThat(empty.itemCount()).isZero();
        assertThat(empty.items()).isEmpty();
        assertThat(dailyBriefRepository.count()).isEqualTo(1);
        assertThat(dailyBriefItemRepository.count()).isZero();
    }

    @Test
    void rejectsUnknownDisabledWatchlistsAndInvalidDirectLimits() {
        assertThatThrownBy(() -> dailyBriefService.generate(
                new GenerateDailyBriefRequest(999L, BRIEF_DATE, null)
        )).isInstanceOf(WatchlistNotFoundException.class);

        Watchlist disabled = new Watchlist("Disabled");
        disabled.setEnabled(false);
        watchlistRepository.saveAndFlush(disabled);
        assertThatThrownBy(() -> dailyBriefService.generate(
                new GenerateDailyBriefRequest(disabled.getId(), BRIEF_DATE, null)
        )).isInstanceOf(DailyBriefWatchlistDisabledException.class);

        Watchlist enabled = new Watchlist("Enabled");
        watchlistRepository.saveAndFlush(enabled);
        assertThatIllegalArgumentException().isThrownBy(() -> dailyBriefService.generate(
                new GenerateDailyBriefRequest(enabled.getId(), BRIEF_DATE, 0)
        )).withMessage("Daily brief max items must be between 1 and 20");
        assertThatIllegalArgumentException().isThrownBy(() -> dailyBriefService.generate(
                new GenerateDailyBriefRequest(enabled.getId(), BRIEF_DATE, 21)
        )).withMessage("Daily brief max items must be between 1 and 20");
    }

    private Source saveSource() {
        return sourceRepository.saveAndFlush(new Source(
                "Publisher",
                "https://publisher.example",
                SourceType.WEBSITE,
                SourcePriority.MEDIUM
        ));
    }

    private Article saveArticle(
            Source source,
            String title,
            String description,
            Instant publishedAt,
            Instant collectedAt
    ) {
        Article article = new Article(
                title,
                "https://publisher.example/" + title.toLowerCase().replace(' ', '-'),
                source,
                collectedAt
        );
        article.setDescription(description);
        article.setPublishedAt(publishedAt);
        return articleRepository.saveAndFlush(article);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClockConfiguration {

        @Bean
        @Primary
        Clock fixedDailyBriefClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }
}
