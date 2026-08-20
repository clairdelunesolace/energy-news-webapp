package com.carya.energynews.translation;

import com.carya.energynews.article.Article;
import com.carya.energynews.article.ArticleRepository;
import com.carya.energynews.source.Source;
import com.carya.energynews.source.SourceLanguage;
import com.carya.energynews.source.SourcePriority;
import com.carya.energynews.source.SourceRepository;
import com.carya.energynews.source.SourceType;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class TranslationBackfillRepositoryTest {

    @Autowired
    private ArticleRepository articleRepository;

    @Autowired
    private ArticleTranslationRepository articleTranslationRepository;

    @Autowired
    private SourceRepository sourceRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void selectsOnlyEnglishArticlesWithoutSuccessfulChineseTranslation() {
        Source englishSource = saveSource("candidate-en", SourceLanguage.EN);
        Source chineseSource = saveSource("candidate-zh", SourceLanguage.ZH_CN);
        Article missing = saveArticle(
                englishSource,
                "candidate-missing",
                Instant.parse("2026-08-20T00:00:00Z"),
                Instant.parse("2026-08-20T01:00:00Z")
        );
        Article failed = saveArticle(
                englishSource,
                "candidate-failed",
                Instant.parse("2026-08-19T00:00:00Z"),
                Instant.parse("2026-08-19T01:00:00Z")
        );
        saveTranslation(failed, TranslationStatus.FAILED);
        Article pending = saveArticle(
                englishSource,
                "candidate-pending",
                null,
                Instant.parse("2026-08-22T01:00:00Z")
        );
        saveTranslation(pending, TranslationStatus.PENDING);
        Article successful = saveArticle(
                englishSource,
                "candidate-success",
                Instant.parse("2026-08-21T00:00:00Z"),
                Instant.parse("2026-08-21T01:00:00Z")
        );
        saveTranslation(successful, TranslationStatus.SUCCESS);
        saveArticle(
                chineseSource,
                "candidate-chinese",
                Instant.parse("2026-08-23T00:00:00Z"),
                Instant.parse("2026-08-23T01:00:00Z")
        );
        entityManager.clear();

        List<Article> candidates = findCandidates(10);

        assertThat(candidates)
                .extracting(Article::getId)
                .containsExactly(missing.getId(), failed.getId(), pending.getId());
        assertThat(candidates).allSatisfy(article -> {
            assertThat(article.getSource().getLanguage()).isEqualTo(SourceLanguage.EN);
            assertThat(entityManager.getEntityManagerFactory().getPersistenceUnitUtil()
                    .isLoaded(article, "source")).isTrue();
        });
    }

    @Test
    void ordersNewestFirstAndRespectsTheLimit() {
        Source source = saveSource("candidate-order", SourceLanguage.EN);
        Article oldestPublished = saveArticle(
                source,
                "candidate-oldest",
                Instant.parse("2026-08-18T00:00:00Z"),
                Instant.parse("2026-08-18T01:00:00Z")
        );
        Article tiedOlder = saveArticle(
                source,
                "candidate-tied-older",
                Instant.parse("2026-08-20T00:00:00Z"),
                Instant.parse("2026-08-20T01:00:00Z")
        );
        Article tiedNewer = saveArticle(
                source,
                "candidate-tied-newer",
                Instant.parse("2026-08-20T00:00:00Z"),
                Instant.parse("2026-08-20T02:00:00Z")
        );
        Article newestPublished = saveArticle(
                source,
                "candidate-newest",
                Instant.parse("2026-08-21T00:00:00Z"),
                Instant.parse("2026-08-21T01:00:00Z")
        );
        Article nullPublished = saveArticle(
                source,
                "candidate-null",
                null,
                Instant.parse("2026-08-22T01:00:00Z")
        );
        entityManager.clear();

        List<Article> allCandidates = findCandidates(10);
        List<Article> limitedCandidates = findCandidates(3);

        assertThat(allCandidates)
                .extracting(Article::getId)
                .containsExactly(
                        newestPublished.getId(),
                        tiedNewer.getId(),
                        tiedOlder.getId(),
                        oldestPublished.getId(),
                        nullPublished.getId()
                );
        assertThat(limitedCandidates)
                .extracting(Article::getId)
                .containsExactly(newestPublished.getId(), tiedNewer.getId(), tiedOlder.getId());
    }

    private List<Article> findCandidates(int limit) {
        return articleRepository.findTranslationBackfillCandidates(
                SourceLanguage.EN,
                TranslationLanguage.ZH_CN,
                TranslationStatus.SUCCESS,
                PageRequest.of(0, limit)
        );
    }

    private Source saveSource(String suffix, SourceLanguage language) {
        return sourceRepository.saveAndFlush(new Source(
                "Source " + suffix,
                "https://example.com/sources/" + suffix,
                SourceType.RSS,
                SourcePriority.MEDIUM,
                language
        ));
    }

    private Article saveArticle(
            Source source,
            String suffix,
            Instant publishedAt,
            Instant collectedAt
    ) {
        Article article = new Article(
                "Article " + suffix,
                "https://example.com/articles/" + suffix,
                source,
                collectedAt
        );
        article.setPublishedAt(publishedAt);
        return articleRepository.saveAndFlush(article);
    }

    private void saveTranslation(Article article, TranslationStatus status) {
        ArticleTranslation translation = new ArticleTranslation(
                article,
                TranslationLanguage.ZH_CN
        );
        translation.setStatus(status);
        articleTranslationRepository.saveAndFlush(translation);
    }
}
