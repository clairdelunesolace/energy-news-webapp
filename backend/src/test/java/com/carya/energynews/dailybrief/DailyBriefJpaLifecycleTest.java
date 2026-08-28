package com.carya.energynews.dailybrief;

import com.carya.energynews.article.Article;
import com.carya.energynews.article.ArticleRepository;
import com.carya.energynews.source.Source;
import com.carya.energynews.source.SourcePriority;
import com.carya.energynews.source.SourceRepository;
import com.carya.energynews.source.SourceType;
import com.carya.energynews.translation.ArticleTranslationRepository;
import com.carya.energynews.watchlist.Watchlist;
import com.carya.energynews.watchlist.WatchlistRepository;
import com.carya.energynews.watchlistdiscovery.ArticleKeywordMatchRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "app.security.admin.password=test-password",
        "app.discovery.provider=none",
        "app.discovery.scheduler.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:daily-brief-lifecycle;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.open-in-view=false"
})
class DailyBriefJpaLifecycleTest {

    private static final Instant START = Instant.parse("2026-08-27T16:00:00Z");
    private static final Instant END = Instant.parse("2026-08-28T16:00:00Z");

    @Autowired
    private DailyBriefRepository dailyBriefRepository;

    @Autowired
    private DailyBriefItemRepository dailyBriefItemRepository;

    @Autowired
    private WatchlistRepository watchlistRepository;

    @Autowired
    private ArticleRepository articleRepository;

    @Autowired
    private SourceRepository sourceRepository;

    @Autowired
    private ArticleKeywordMatchRepository matchRepository;

    @Autowired
    private ArticleTranslationRepository translationRepository;

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
    void deletingBriefDeletesItemsButNeverArticle() {
        Snapshot snapshot = saveSnapshot("Delete brief", "delete-brief");

        dailyBriefRepository.deleteById(snapshot.briefId());
        dailyBriefRepository.flush();

        assertThat(dailyBriefItemRepository.count()).isZero();
        assertThat(articleRepository.existsById(snapshot.articleId())).isTrue();
        assertThat(watchlistRepository.existsById(snapshot.watchlistId())).isTrue();
    }

    @Test
    void deletingArticleDeletesOnlyReferencingItems() {
        Snapshot snapshot = saveSnapshot("Delete article", "delete-article");

        articleRepository.deleteById(snapshot.articleId());
        articleRepository.flush();

        assertThat(dailyBriefItemRepository.count()).isZero();
        assertThat(dailyBriefRepository.existsById(snapshot.briefId())).isTrue();
        assertThat(watchlistRepository.existsById(snapshot.watchlistId())).isTrue();
    }

    @Test
    void deletingWatchlistDeletesBriefAndItemsButNotArticle() {
        Snapshot snapshot = saveSnapshot("Delete watchlist", "delete-watchlist");

        watchlistRepository.deleteById(snapshot.watchlistId());
        watchlistRepository.flush();

        assertThat(dailyBriefRepository.existsById(snapshot.briefId())).isFalse();
        assertThat(dailyBriefItemRepository.count()).isZero();
        assertThat(articleRepository.existsById(snapshot.articleId())).isTrue();
    }

    private Snapshot saveSnapshot(String watchlistName, String slug) {
        Source source = sourceRepository.saveAndFlush(new Source(
                "Publisher " + slug,
                "https://" + slug + ".example",
                SourceType.WEBSITE,
                SourcePriority.MEDIUM
        ));
        Watchlist watchlist = watchlistRepository.saveAndFlush(new Watchlist(watchlistName));
        Article article = articleRepository.saveAndFlush(new Article(
                "Article " + slug,
                "https://publisher.example/" + slug,
                source,
                START.plusSeconds(3_600)
        ));
        DailyBrief brief = dailyBriefRepository.saveAndFlush(new DailyBrief(
                watchlist,
                LocalDate.parse("2026-08-28"),
                "Asia/Shanghai",
                START,
                END,
                1
        ));
        dailyBriefItemRepository.saveAndFlush(new DailyBriefItem(brief, article, 1));
        return new Snapshot(brief.getId(), watchlist.getId(), article.getId());
    }

    private record Snapshot(Long briefId, Long watchlistId, Long articleId) {
    }
}
