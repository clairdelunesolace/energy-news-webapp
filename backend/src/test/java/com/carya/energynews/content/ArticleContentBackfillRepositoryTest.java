package com.carya.energynews.content;

import com.carya.energynews.article.Article;
import com.carya.energynews.article.ArticleRepository;
import com.carya.energynews.source.Source;
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
class ArticleContentBackfillRepositoryTest {

    @Autowired
    private ArticleRepository articleRepository;

    @Autowired
    private SourceRepository sourceRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void selectsOnlyMissingContentInFeedOrderAndAppliesTheDatabaseLimit() {
        Source source = sourceRepository.saveAndFlush(new Source(
                "Content source",
                "https://example.com/content-feed",
                SourceType.RSS,
                SourcePriority.MEDIUM
        ));
        Article newest = save(source, "newest", null,
                Instant.parse("2026-08-20T12:00:00Z"),
                Instant.parse("2026-08-20T13:00:00Z"));
        Article tiedOlder = save(source, "tied-older", "   ",
                Instant.parse("2026-08-19T12:00:00Z"),
                Instant.parse("2026-08-19T13:00:00Z"));
        Article tiedNewer = save(source, "tied-newer", null,
                Instant.parse("2026-08-19T12:00:00Z"),
                Instant.parse("2026-08-19T14:00:00Z"));
        Article nullPublished = save(source, "null-published", null, null,
                Instant.parse("2026-08-21T13:00:00Z"));
        save(source, "already-filled", "Existing content",
                Instant.parse("2026-08-21T12:00:00Z"),
                Instant.parse("2026-08-21T13:00:00Z"));
        entityManager.clear();

        List<Article> allCandidates = articleRepository.findContentBackfillCandidates(
                PageRequest.of(0, 10)
        );
        List<Article> limitedCandidates = articleRepository.findContentBackfillCandidates(
                PageRequest.of(0, 2)
        );

        assertThat(allCandidates).extracting(Article::getId)
                .containsExactly(
                        newest.getId(),
                        tiedNewer.getId(),
                        tiedOlder.getId(),
                        nullPublished.getId()
                );
        assertThat(limitedCandidates).extracting(Article::getId)
                .containsExactly(newest.getId(), tiedNewer.getId());
    }

    private Article save(
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
}
