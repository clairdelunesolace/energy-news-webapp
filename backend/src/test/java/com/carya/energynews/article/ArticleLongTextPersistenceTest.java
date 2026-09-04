package com.carya.energynews.article;

import com.carya.energynews.collection.CollectedArticle;
import com.carya.energynews.source.Source;
import com.carya.energynews.source.SourcePriority;
import com.carya.energynews.source.SourceRepository;
import com.carya.energynews.source.SourceType;
import jakarta.persistence.Column;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(ArticleIngestionService.class)
class ArticleLongTextPersistenceTest {

    private static final Instant PUBLISHED_AT = Instant.parse("2026-09-03T06:00:00Z");

    @Autowired
    private ArticleIngestionService articleIngestionService;

    @Autowired
    private ArticleRepository articleRepository;

    @Autowired
    private SourceRepository sourceRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void ingestsAndRoundTripsLongTitleAndUrlWithoutChangingDuplicateDetection() {
        Source source = sourceRepository.saveAndFlush(new Source(
                "Long text source",
                "https://example.com/feed",
                SourceType.RSS,
                SourcePriority.HIGH
        ));
        String title = "Long storage headline — " + "储能与微电网".repeat(60);
        String url = "https://example.com/articles/" + "long-path-segment-".repeat(30);
        assertThat(title.length()).isGreaterThan(255);
        assertThat(url.length()).isGreaterThan(255);
        CollectedArticle collected = new CollectedArticle(
                title,
                url,
                "Original summary",
                "Original content",
                PUBLISHED_AT,
                source.getId()
        );

        ArticleIngestionResult saved = articleIngestionService.ingest(collected);
        Long articleId = saved.article().getId();
        entityManager.clear();

        Article reloaded = articleRepository.findById(articleId).orElseThrow();
        assertThat(saved.status()).isEqualTo(ArticleIngestionResult.Status.SAVED);
        assertThat(reloaded.getTitle()).isEqualTo(title);
        assertThat(reloaded.getUrl()).isEqualTo(url);
        assertThat(reloaded.getTitle()).hasSize(title.length());
        assertThat(reloaded.getUrl()).hasSize(url.length());

        ArticleIngestionResult duplicate = articleIngestionService.ingest(collected);

        assertThat(duplicate.status()).isEqualTo(ArticleIngestionResult.Status.DUPLICATE);
        assertThat(duplicate.article().getId()).isEqualTo(articleId);
        assertThat(articleRepository.count()).isOne();
    }

    @Test
    void mapsRequiredTitleAndUrlToColumnsWithCapacityBeyond255Characters() throws Exception {
        Column title = Article.class.getDeclaredField("title").getAnnotation(Column.class);
        Column url = Article.class.getDeclaredField("url").getAnnotation(Column.class);

        assertThat(title.nullable()).isFalse();
        assertThat(url.nullable()).isFalse();
        assertThat(title.columnDefinition()).isEqualTo("text");
        assertThat(url.columnDefinition()).isEqualTo("text");

        for (String column : new String[]{"TITLE", "URL"}) {
            Long maximumLength = jdbcTemplate.queryForObject("""
                    SELECT CHARACTER_MAXIMUM_LENGTH
                    FROM INFORMATION_SCHEMA.COLUMNS
                    WHERE TABLE_NAME = 'ARTICLES' AND COLUMN_NAME = ?
                    """, Long.class, column);
            assertThat(maximumLength).isGreaterThan(255L);
        }
    }
}
