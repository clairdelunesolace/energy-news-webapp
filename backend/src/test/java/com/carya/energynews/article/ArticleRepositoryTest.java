package com.carya.energynews.article;

import com.carya.energynews.source.Source;
import com.carya.energynews.source.SourcePriority;
import com.carya.energynews.source.SourceRepository;
import com.carya.energynews.source.SourceType;
import com.carya.energynews.watchlist.Keyword;
import com.carya.energynews.watchlist.Watchlist;
import com.carya.energynews.watchlistdiscovery.ArticleKeywordMatch;
import jakarta.persistence.EntityManager;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
class ArticleRepositoryTest {

    private static final Instant COLLECTED_AT = Instant.parse("2026-08-19T06:00:00Z");

    @Autowired
    private ArticleRepository articleRepository;

    @Autowired
    private SourceRepository sourceRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void savesArticle() {
        Source source = saveSource("save");
        Article article = new Article(
                "Battery storage deployment grows",
                "https://example.com/articles/storage-growth",
                source,
                COLLECTED_AT
        );
        article.setDescription("A short summary");
        article.setContent("The complete article content");
        article.setPublishedAt(Instant.parse("2026-08-18T12:00:00Z"));

        Article saved = articleRepository.saveAndFlush(article);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getTitle()).isEqualTo("Battery storage deployment grows");
        assertThat(saved.getDescription()).isEqualTo("A short summary");
        assertThat(saved.getContent()).isEqualTo("The complete article content");
        assertThat(saved.getPublishedAt()).isEqualTo(Instant.parse("2026-08-18T12:00:00Z"));
        assertThat(saved.getCollectedAt()).isEqualTo(COLLECTED_AT);
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isEqualTo(saved.getCreatedAt());
    }

    @Test
    void loadsSourceRelationship() {
        Source source = saveSource("relationship");
        Article saved = articleRepository.saveAndFlush(new Article(
                "Article with source",
                "https://example.com/articles/with-source",
                source,
                COLLECTED_AT
        ));
        entityManager.clear();

        Article loaded = articleRepository.findById(saved.getId()).orElseThrow();

        assertThat(loaded.getSource().getId()).isEqualTo(source.getId());
        assertThat(loaded.getSource().getName()).isEqualTo("Source relationship");
    }

    @Test
    void findsCanonicalArticleByUrlWithSourceLoaded() {
        Source source = saveSource("canonical-url");
        Article saved = articleRepository.saveAndFlush(new Article(
                "Canonical article",
                "https://example.com/articles/canonical-url",
                source,
                COLLECTED_AT
        ));
        entityManager.clear();

        Article loaded = articleRepository.findByUrl(saved.getUrl()).orElseThrow();

        assertThat(loaded.getId()).isEqualTo(saved.getId());
        assertThat(entityManager.getEntityManagerFactory().getPersistenceUnitUtil()
                .isLoaded(loaded, "source")).isTrue();
        assertThat(loaded.getSource().getId()).isEqualTo(source.getId());
    }

    @Test
    void pagesArticlesWithNewestStableNullSafeOrderingAndLoadedSources() {
        Source source = saveSource("paging-order");
        Article oldestPublished = saveArticle(
                source,
                "oldest-published",
                Instant.parse("2026-08-17T12:00:00Z"),
                Instant.parse("2026-08-19T01:00:00Z")
        );
        Article tiedFirst = saveArticle(
                source,
                "tied-first",
                Instant.parse("2026-08-18T12:00:00Z"),
                Instant.parse("2026-08-19T02:00:00Z")
        );
        Article tiedSecond = saveArticle(
                source,
                "tied-second",
                Instant.parse("2026-08-18T12:00:00Z"),
                Instant.parse("2026-08-19T02:00:00Z")
        );
        Article newestPublished = saveArticle(
                source,
                "newest-published",
                Instant.parse("2026-08-19T12:00:00Z"),
                Instant.parse("2026-08-19T03:00:00Z")
        );
        Article nullPublishedOlder = saveArticle(
                source,
                "null-published-older",
                null,
                Instant.parse("2026-08-19T04:00:00Z")
        );
        Article nullPublishedNewer = saveArticle(
                source,
                "null-published-newer",
                null,
                Instant.parse("2026-08-20T04:00:00Z")
        );
        entityManager.clear();

        Page<Article> firstPage = articleRepository.findAllFiltered(null, "", null, PageRequest.of(0, 4));
        Page<Article> secondPage = articleRepository.findAllFiltered(null, "", null, PageRequest.of(1, 4));

        assertThat(firstPage.getContent())
                .extracting(Article::getId)
                .containsExactly(
                        newestPublished.getId(),
                        tiedSecond.getId(),
                        tiedFirst.getId(),
                        oldestPublished.getId()
                );
        assertThat(secondPage.getContent())
                .extracting(Article::getId)
                .containsExactly(nullPublishedNewer.getId(), nullPublishedOlder.getId());
        assertThat(firstPage.getTotalElements()).isEqualTo(6);
        assertThat(firstPage.getTotalPages()).isEqualTo(2);
        assertThat(firstPage.isFirst()).isTrue();
        assertThat(firstPage.isLast()).isFalse();
        assertThat(secondPage.isLast()).isTrue();
        assertThat(firstPage.getContent()).allSatisfy(article ->
                assertThat(entityManager.getEntityManagerFactory().getPersistenceUnitUtil()
                        .isLoaded(article, "source")).isTrue()
        );
    }

    @Test
    void filtersBySourceAndCaseInsensitiveKeywordInTitleOrDescription() {
        Source selectedSource = saveSource("filter-selected");
        Source otherSource = saveSource("filter-other");
        Article titleMatch = saveArticle(
                selectedSource,
                "filter-title-match",
                "Battery storage deployment",
                null,
                Instant.parse("2026-08-20T12:00:00Z"),
                COLLECTED_AT
        );
        Article descriptionMatch = saveArticle(
                selectedSource,
                "filter-description-match",
                "Grid project commissioned",
                "The project includes a BATTERY system",
                Instant.parse("2026-08-19T12:00:00Z"),
                COLLECTED_AT
        );
        Article sourceOnly = saveArticle(
                selectedSource,
                "filter-source-only",
                "Solar project commissioned",
                "Photovoltaic generation",
                Instant.parse("2026-08-21T12:00:00Z"),
                COLLECTED_AT
        );
        Article keywordOnly = saveArticle(
                otherSource,
                "filter-keyword-only",
                "Battery project from another source",
                null,
                Instant.parse("2026-08-22T12:00:00Z"),
                COLLECTED_AT
        );
        entityManager.clear();

        Page<Article> sourceResults = articleRepository.findAllFiltered(
                selectedSource.getId(),
                "",
                null,
                PageRequest.of(0, 10)
        );
        Page<Article> keywordResults = articleRepository.findAllFiltered(
                null,
                "battery",
                null,
                PageRequest.of(0, 10)
        );
        Page<Article> combinedResults = articleRepository.findAllFiltered(
                selectedSource.getId(),
                "battery",
                null,
                PageRequest.of(0, 10)
        );

        assertThat(sourceResults.getContent())
                .extracting(Article::getId)
                .containsExactly(sourceOnly.getId(), titleMatch.getId(), descriptionMatch.getId());
        assertThat(keywordResults.getContent())
                .extracting(Article::getId)
                .containsExactly(keywordOnly.getId(), titleMatch.getId(), descriptionMatch.getId());
        assertThat(combinedResults.getContent())
                .extracting(Article::getId)
                .containsExactly(titleMatch.getId(), descriptionMatch.getId());
    }

    @Test
    void paginatesFilteredResultsAndReturnsValidEmptyPageMetadata() {
        Source source = saveSource("filter-paging");
        Article newest = saveArticle(
                source,
                "filter-paging-newest",
                "Newest storage article",
                null,
                Instant.parse("2026-08-20T12:00:00Z"),
                COLLECTED_AT
        );
        Article middle = saveArticle(
                source,
                "filter-paging-middle",
                "Middle storage article",
                null,
                Instant.parse("2026-08-19T12:00:00Z"),
                COLLECTED_AT
        );
        Article oldest = saveArticle(
                source,
                "filter-paging-oldest",
                "Oldest storage article",
                null,
                Instant.parse("2026-08-18T12:00:00Z"),
                COLLECTED_AT
        );
        saveArticle(
                source,
                "filter-paging-non-match",
                "Unrelated market article",
                null,
                Instant.parse("2026-08-21T12:00:00Z"),
                COLLECTED_AT
        );
        entityManager.clear();

        Page<Article> firstPage = articleRepository.findAllFiltered(
                null,
                "storage",
                null,
                PageRequest.of(0, 2)
        );
        Page<Article> secondPage = articleRepository.findAllFiltered(
                null,
                "storage",
                null,
                PageRequest.of(1, 2)
        );
        Page<Article> noMatches = articleRepository.findAllFiltered(
                Long.MAX_VALUE,
                "",
                null,
                PageRequest.of(0, 20)
        );

        assertThat(firstPage.getContent())
                .extracting(Article::getId)
                .containsExactly(newest.getId(), middle.getId());
        assertThat(secondPage.getContent())
                .extracting(Article::getId)
                .containsExactly(oldest.getId());
        assertThat(firstPage.getTotalElements()).isEqualTo(3);
        assertThat(firstPage.getTotalPages()).isEqualTo(2);
        assertThat(firstPage.isLast()).isFalse();
        assertThat(secondPage.isLast()).isTrue();
        assertThat(noMatches.getContent()).isEmpty();
        assertThat(noMatches.getTotalElements()).isZero();
        assertThat(noMatches.getTotalPages()).isZero();
        assertThat(noMatches.isFirst()).isTrue();
        assertThat(noMatches.isLast()).isTrue();
    }

    @Test
    void filtersByExactKeywordIdWithStablePagesLoadedSourcesAndNoDuplicateArticles() {
        Source source = saveSource("keyword-pages");
        Watchlist watchlist = new Watchlist("Topics");
        Keyword selected = watchlist.addKeyword("grid storage");
        Keyword other = watchlist.addKeyword("long duration");
        Watchlist otherWatchlist = new Watchlist("Other topics");
        Keyword sameTextDifferentId = otherWatchlist.addKeyword("grid storage");
        entityManager.persist(watchlist);
        entityManager.persist(otherWatchlist);
        Article newest = saveArticle(source, "matched-newest", COLLECTED_AT, COLLECTED_AT);
        Article oldest = saveArticle(source, "matched-oldest", null, COLLECTED_AT);
        Article otherOnly = saveArticle(source, "other-keyword", null, COLLECTED_AT);
        Article unmatchedRss = saveArticle(source, "unmatched-rss", null, COLLECTED_AT);
        entityManager.persist(new ArticleKeywordMatch(newest, selected));
        entityManager.persist(new ArticleKeywordMatch(newest, other));
        entityManager.persist(new ArticleKeywordMatch(oldest, selected));
        entityManager.persist(new ArticleKeywordMatch(otherOnly, sameTextDifferentId));
        entityManager.flush();
        entityManager.clear();

        Page<Article> first = articleRepository.findAllFiltered(null, "", selected.getId(), PageRequest.of(0, 1));
        Page<Article> second = articleRepository.findAllFiltered(null, "", selected.getId(), PageRequest.of(1, 1));

        assertThat(first.getContent()).extracting(Article::getId).containsExactly(newest.getId());
        assertThat(second.getContent()).extracting(Article::getId).containsExactly(oldest.getId());
        assertThat(first.getTotalElements()).isEqualTo(2);
        assertThat(first.getTotalPages()).isEqualTo(2);
        assertThat(first.isLast()).isFalse();
        assertThat(second.getTotalElements()).isEqualTo(2);
        assertThat(second.isLast()).isTrue();
        assertThat(entityManager.getEntityManagerFactory().getPersistenceUnitUtil()
                .isLoaded(first.getContent().getFirst(), "source")).isTrue();

        Page<Article> all = articleRepository.findAllFiltered(null, "", null, PageRequest.of(0, 10));
        assertThat(all.getContent()).extracting(Article::getId)
                .containsExactly(newest.getId(), unmatchedRss.getId(), otherOnly.getId(), oldest.getId());
        assertThat(all.getTotalElements()).isEqualTo(4);
        Page<Article> unknown = articleRepository.findAllFiltered(null, "", Long.MAX_VALUE, PageRequest.of(0, 10));
        assertThat(unknown.getContent()).isEmpty();
        assertThat(unknown.getTotalElements()).isZero();
        assertThat(unknown.getTotalPages()).isZero();
    }

    @Test
    void combinesKeywordIdWithTextSearchAndOptionalSourceFilter() {
        Source selectedSource = saveSource("keyword-combined");
        Source otherSource = saveSource("keyword-other");
        Watchlist watchlist = new Watchlist("Combined topics");
        Keyword selected = watchlist.addKeyword("topic independent of article text");
        entityManager.persist(watchlist);
        Article titleMatch = saveArticle(selectedSource, "combined-title", "BATTERY deployment", null,
                COLLECTED_AT, COLLECTED_AT);
        Article descriptionMatch = saveArticle(selectedSource, "combined-description", "Grid project", "Battery system",
                null, COLLECTED_AT);
        Article otherSourceMatch = saveArticle(otherSource, "combined-other-source", "Battery market", null,
                null, COLLECTED_AT);
        Article noTextMatch = saveArticle(selectedSource, "combined-no-text", null, COLLECTED_AT);
        saveArticle(selectedSource, "combined-unmatched", "Battery news without explicit match", null,
                COLLECTED_AT, COLLECTED_AT);
        for (Article article : List.of(titleMatch, descriptionMatch, otherSourceMatch, noTextMatch)) {
            entityManager.persist(new ArticleKeywordMatch(article, selected));
        }
        entityManager.flush();
        entityManager.clear();

        Page<Article> combined = articleRepository.findAllFiltered(
                selectedSource.getId(), "battery", selected.getId(), PageRequest.of(0, 1));
        Page<Article> second = articleRepository.findAllFiltered(
                selectedSource.getId(), "battery", selected.getId(), PageRequest.of(1, 1));
        assertThat(combined.getContent()).extracting(Article::getId).containsExactly(titleMatch.getId());
        assertThat(second.getContent()).extracting(Article::getId).containsExactly(descriptionMatch.getId());
        assertThat(combined.getTotalElements()).isEqualTo(2);
        assertThat(combined.getTotalPages()).isEqualTo(2);
        assertThat(articleRepository.findAllFiltered(null, "battery", selected.getId(), PageRequest.of(0, 10))
                .getContent()).extracting(Article::getId)
                .containsExactly(titleMatch.getId(), otherSourceMatch.getId(), descriptionMatch.getId());
    }

    @Test
    void rejectsDuplicateUrl() {
        Source source = saveSource("duplicate");
        articleRepository.saveAndFlush(new Article(
                "First article",
                "https://example.com/articles/duplicate",
                source,
                COLLECTED_AT
        ));

        Article duplicate = new Article(
                "Second article",
                "https://example.com/articles/duplicate",
                source,
                COLLECTED_AT
        );

        assertThatThrownBy(() -> articleRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsMissingSource() {
        Article article = new Article(
                "Article without source",
                "https://example.com/articles/no-source",
                null,
                COLLECTED_AT
        );

        assertThatThrownBy(() -> articleRepository.saveAndFlush(article))
                .isInstanceOf(ConstraintViolationException.class);
    }

    @Test
    void rejectsMissingTitle() {
        Article article = new Article(
                null,
                "https://example.com/articles/no-title",
                saveSource("no-title"),
                COLLECTED_AT
        );

        assertThatThrownBy(() -> articleRepository.saveAndFlush(article))
                .isInstanceOf(ConstraintViolationException.class);
    }

    @Test
    void rejectsMissingUrl() {
        Article article = new Article(
                "Article without URL",
                null,
                saveSource("no-url"),
                COLLECTED_AT
        );

        assertThatThrownBy(() -> articleRepository.saveAndFlush(article))
                .isInstanceOf(ConstraintViolationException.class);
    }

    @Test
    void rejectsMissingCollectedAt() {
        Article article = new Article(
                "Article without collection time",
                "https://example.com/articles/no-collected-at",
                saveSource("no-collected-at"),
                null
        );

        assertThatThrownBy(() -> articleRepository.saveAndFlush(article))
                .isInstanceOf(ConstraintViolationException.class);
    }

    private Source saveSource(String suffix) {
        return sourceRepository.saveAndFlush(new Source(
                "Source " + suffix,
                "https://example.com/sources/" + suffix,
                SourceType.RSS,
                SourcePriority.MEDIUM
        ));
    }

    private Article saveArticle(
            Source source,
            String suffix,
            Instant publishedAt,
            Instant collectedAt
    ) {
        return saveArticle(
                source,
                suffix,
                "Article " + suffix,
                null,
                publishedAt,
                collectedAt
        );
    }

    private Article saveArticle(
            Source source,
            String suffix,
            String title,
            String description,
            Instant publishedAt,
            Instant collectedAt
    ) {
        Article article = new Article(
                title,
                "https://example.com/articles/" + suffix,
                source,
                collectedAt
        );
        article.setDescription(description);
        article.setPublishedAt(publishedAt);
        return articleRepository.saveAndFlush(article);
    }
}
