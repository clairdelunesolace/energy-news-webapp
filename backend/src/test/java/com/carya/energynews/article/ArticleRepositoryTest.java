package com.carya.energynews.article;

import com.carya.energynews.source.Source;
import com.carya.energynews.source.SourcePriority;
import com.carya.energynews.source.SourceRepository;
import com.carya.energynews.source.SourceType;
import jakarta.persistence.EntityManager;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;

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
}
