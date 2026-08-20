package com.carya.energynews.translation;

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
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
class ArticleTranslationRepositoryTest {

    private static final Instant COLLECTED_AT = Instant.parse("2026-08-20T01:00:00Z");
    private static final Instant TRANSLATED_AT = Instant.parse("2026-08-20T02:00:00Z");

    @Autowired
    private ArticleTranslationRepository articleTranslationRepository;

    @Autowired
    private ArticleRepository articleRepository;

    @Autowired
    private SourceRepository sourceRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void persistsPendingTranslationWithNullableFieldsAndTimestamps() {
        Article article = saveArticle("pending");
        ArticleTranslation translation = new ArticleTranslation(
                article,
                TranslationLanguage.ZH_CN
        );

        ArticleTranslation saved = articleTranslationRepository.saveAndFlush(translation);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getArticle().getId()).isEqualTo(article.getId());
        assertThat(saved.getLanguage()).isEqualTo(TranslationLanguage.ZH_CN);
        assertThat(saved.getStatus()).isEqualTo(TranslationStatus.PENDING);
        assertThat(saved.getTitle()).isNull();
        assertThat(saved.getDescription()).isNull();
        assertThat(saved.getTranslatedAt()).isNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isEqualTo(saved.getCreatedAt());
    }

    @Test
    void persistsSuccessfulTranslation() {
        ArticleTranslation translation = new ArticleTranslation(
                saveArticle("success"),
                TranslationLanguage.ZH_CN
        );
        translation.setTitle("储能部署加速");
        translation.setDescription("电池储能项目正在快速增长。");
        translation.setStatus(TranslationStatus.SUCCESS);
        translation.setTranslatedAt(TRANSLATED_AT);

        ArticleTranslation saved = articleTranslationRepository.saveAndFlush(translation);

        assertThat(saved.getTitle()).isEqualTo("储能部署加速");
        assertThat(saved.getDescription()).isEqualTo("电池储能项目正在快速增长。");
        assertThat(saved.getStatus()).isEqualTo(TranslationStatus.SUCCESS);
        assertThat(saved.getTranslatedAt()).isEqualTo(TRANSLATED_AT);
    }

    @Test
    void findsTranslationByArticleIdAndLanguage() {
        Article article = saveArticle("lookup");
        ArticleTranslation saved = articleTranslationRepository.saveAndFlush(
                new ArticleTranslation(article, TranslationLanguage.ZH_CN)
        );
        entityManager.clear();

        Optional<ArticleTranslation> found = articleTranslationRepository.findByArticleIdAndLanguage(
                article.getId(),
                TranslationLanguage.ZH_CN
        );

        assertThat(found).isPresent();
        ArticleTranslation loaded = found.orElseThrow();
        assertThat(loaded.getId()).isEqualTo(saved.getId());
        assertThat(loaded.getArticle().getId()).isEqualTo(article.getId());
    }

    @Test
    void rejectsDuplicateArticleAndLanguage() {
        Article article = saveArticle("duplicate");
        articleTranslationRepository.saveAndFlush(
                new ArticleTranslation(article, TranslationLanguage.ZH_CN)
        );
        ArticleTranslation duplicate = new ArticleTranslation(
                article,
                TranslationLanguage.ZH_CN
        );

        assertThatThrownBy(() -> articleTranslationRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private Article saveArticle(String suffix) {
        Source source = sourceRepository.saveAndFlush(new Source(
                "Translation source " + suffix,
                "https://example.com/sources/translation-" + suffix,
                SourceType.RSS,
                SourcePriority.MEDIUM
        ));
        return articleRepository.saveAndFlush(new Article(
                "Original article " + suffix,
                "https://example.com/articles/translation-" + suffix,
                source,
                COLLECTED_AT
        ));
    }
}
