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
class ArticleContentTranslationBackfillRepositoryTest {

    private static final List<ContentTranslationStatus> RETRY_STATUSES = List.of(
            ContentTranslationStatus.PENDING,
            ContentTranslationStatus.FAILED
    );

    @Autowired
    private ArticleTranslationRepository articleTranslationRepository;

    @Autowired
    private ArticleRepository articleRepository;

    @Autowired
    private SourceRepository sourceRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void selectsOnlyEligibleContentTranslationRows() {
        Source english = saveSource("eligible-en", SourceLanguage.EN);
        Source chinese = saveSource("eligible-zh", SourceLanguage.ZH_CN);

        Article nullStatus = saveArticle(english, "null-status", "Original content", 8);
        saveTranslation(nullStatus, TranslationStatus.SUCCESS, null);
        Article pending = saveArticle(english, "pending", "Original content", 7);
        saveTranslation(pending, TranslationStatus.SUCCESS, ContentTranslationStatus.PENDING);
        Article failed = saveArticle(english, "failed", "Original content", 6);
        saveTranslation(failed, TranslationStatus.SUCCESS, ContentTranslationStatus.FAILED);

        Article contentSuccess = saveArticle(english, "content-success", "Original content", 10);
        saveTranslation(
                contentSuccess,
                TranslationStatus.SUCCESS,
                ContentTranslationStatus.SUCCESS
        );
        Article noContent = saveArticle(english, "no-content", null, 9);
        saveTranslation(noContent, TranslationStatus.SUCCESS, null);
        Article blankContent = saveArticle(english, "blank-content", "   ", 9);
        saveTranslation(blankContent, TranslationStatus.SUCCESS, null);
        Article pendingTitle = saveArticle(english, "pending-title", "Original content", 9);
        saveTranslation(pendingTitle, TranslationStatus.PENDING, null);
        Article failedTitle = saveArticle(english, "failed-title", "Original content", 9);
        saveTranslation(failedTitle, TranslationStatus.FAILED, null);
        Article chineseArticle = saveArticle(chinese, "chinese", "Original content", 9);
        saveTranslation(chineseArticle, TranslationStatus.SUCCESS, null);
        saveArticle(english, "missing-translation", "Original content", 9);
        entityManager.clear();

        List<ArticleTranslation> candidates = candidates(10);

        assertThat(candidates)
                .extracting(translation -> translation.getArticle().getId())
                .containsExactly(nullStatus.getId(), pending.getId(), failed.getId());
        assertThat(candidates).allSatisfy(translation -> {
            assertThat(translation.getStatus()).isEqualTo(TranslationStatus.SUCCESS);
            assertThat(translation.getArticle().getContent()).isNotBlank();
            assertThat(translation.getArticle().getSource().getLanguage())
                    .isEqualTo(SourceLanguage.EN);
            assertThat(entityManager.getEntityManagerFactory().getPersistenceUnitUtil()
                    .isLoaded(translation, "article")).isTrue();
            assertThat(entityManager.getEntityManagerFactory().getPersistenceUnitUtil()
                    .isLoaded(translation.getArticle(), "source")).isTrue();
        });
    }

    @Test
    void ordersCandidatesNewestFirstAndAppliesTheDatabaseLimit() {
        Source source = saveSource("content-order", SourceLanguage.EN);
        Article oldest = saveArticle(
                source,
                "oldest",
                "Original content",
                Instant.parse("2026-08-18T00:00:00Z"),
                Instant.parse("2026-08-18T01:00:00Z")
        );
        Article tiedOlder = saveArticle(
                source,
                "tied-older",
                "Original content",
                Instant.parse("2026-08-20T00:00:00Z"),
                Instant.parse("2026-08-20T01:00:00Z")
        );
        Article tiedNewer = saveArticle(
                source,
                "tied-newer",
                "Original content",
                Instant.parse("2026-08-20T00:00:00Z"),
                Instant.parse("2026-08-20T02:00:00Z")
        );
        Article newest = saveArticle(
                source,
                "newest",
                "Original content",
                Instant.parse("2026-08-21T00:00:00Z"),
                Instant.parse("2026-08-21T01:00:00Z")
        );
        Article nullPublished = saveArticle(
                source,
                "null-published",
                "Original content",
                null,
                Instant.parse("2026-08-22T01:00:00Z")
        );
        for (Article article : List.of(oldest, tiedOlder, tiedNewer, newest, nullPublished)) {
            saveTranslation(article, TranslationStatus.SUCCESS, null);
        }
        entityManager.clear();

        List<ArticleTranslation> allCandidates = candidates(10);
        List<ArticleTranslation> limitedCandidates = candidates(3);

        assertThat(allCandidates)
                .extracting(translation -> translation.getArticle().getId())
                .containsExactly(
                        newest.getId(),
                        tiedNewer.getId(),
                        tiedOlder.getId(),
                        oldest.getId(),
                        nullPublished.getId()
                );
        assertThat(limitedCandidates)
                .extracting(translation -> translation.getArticle().getId())
                .containsExactly(newest.getId(), tiedNewer.getId(), tiedOlder.getId());
    }

    private List<ArticleTranslation> candidates(int limit) {
        return articleTranslationRepository.findContentTranslationBackfillCandidates(
                SourceLanguage.EN,
                TranslationLanguage.ZH_CN,
                TranslationStatus.SUCCESS,
                RETRY_STATUSES,
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
            String content,
            int publishedDay
    ) {
        String day = String.format("%02d", publishedDay);
        return saveArticle(
                source,
                suffix,
                content,
                Instant.parse("2026-08-" + day + "T00:00:00Z"),
                Instant.parse("2026-08-" + day + "T01:00:00Z")
        );
    }

    private Article saveArticle(
            Source source,
            String suffix,
            String content,
            Instant publishedAt,
            Instant collectedAt
    ) {
        Article article = new Article(
                "Article " + suffix,
                "https://example.com/articles/" + suffix,
                source,
                collectedAt
        );
        article.setContent(content);
        article.setPublishedAt(publishedAt);
        return articleRepository.saveAndFlush(article);
    }

    private ArticleTranslation saveTranslation(
            Article article,
            TranslationStatus status,
            ContentTranslationStatus contentStatus
    ) {
        ArticleTranslation translation = new ArticleTranslation(
                article,
                TranslationLanguage.ZH_CN
        );
        translation.setStatus(status);
        translation.setContentStatus(contentStatus);
        return articleTranslationRepository.saveAndFlush(translation);
    }
}
