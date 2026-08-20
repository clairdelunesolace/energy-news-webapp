package com.carya.energynews.article;

import com.carya.energynews.source.Source;
import com.carya.energynews.source.SourceLanguage;
import com.carya.energynews.source.SourceNotFoundException;
import com.carya.energynews.source.SourceRepository;
import com.carya.energynews.translation.ArticleTranslation;
import com.carya.energynews.translation.ArticleTranslationRepository;
import com.carya.energynews.translation.TranslationLanguage;
import com.carya.energynews.translation.TranslationStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ArticleServiceTest {

    @Mock
    private ArticleRepository articleRepository;

    @Mock
    private SourceRepository sourceRepository;

    @Mock
    private ArticleTranslationRepository articleTranslationRepository;

    @InjectMocks
    private ArticleService articleService;

    @Test
    void treatsNullKeywordAsNoFilterAndLoadsSuccessfulTranslationsInBatch() {
        Article article = article(SourceLanguage.EN);
        ArticleTranslation translation = translation(article, TranslationStatus.SUCCESS);
        translation.setTitle("储能扩张");
        translation.setDescription("中文摘要");
        PageRequest pageRequest = PageRequest.of(0, 20);
        when(articleRepository.findAllFiltered(null, "", pageRequest))
                .thenReturn(new PageImpl<>(List.of(article), pageRequest, 1));
        when(articleTranslationRepository.findAllByArticleIdInAndLanguageAndStatus(
                List.of(1L),
                TranslationLanguage.ZH_CN,
                TranslationStatus.SUCCESS
        )).thenReturn(List.of(translation));

        ArticlePageResponse page = articleService.getAll(0, 20, null, null);

        assertThat(page.content()).singleElement().satisfies(response -> {
            assertThat(response.id()).isEqualTo(1L);
            assertThat(response.source()).isEqualTo(new ArticleSourceResponse(7L, "Energy Storage News"));
            assertThat(response.original()).isEqualTo(new ArticleOriginalResponse(
                    SourceLanguage.EN,
                    "Stored article",
                    "Original summary",
                    "Original content"
            ));
            assertThat(response.translation()).isEqualTo(new ArticleTranslationResponse(
                    TranslationLanguage.ZH_CN,
                    "储能扩张",
                    "中文摘要"
            ));
        });
        verify(articleRepository).findAllFiltered(null, "", pageRequest);
        verify(articleTranslationRepository).findAllByArticleIdInAndLanguageAndStatus(
                List.of(1L),
                TranslationLanguage.ZH_CN,
                TranslationStatus.SUCCESS
        );
        assertThat(page.page()).isZero();
        assertThat(page.size()).isEqualTo(20);
        assertThat(page.totalElements()).isEqualTo(1);
        assertThat(page.totalPages()).isEqualTo(1);
        assertThat(page.first()).isTrue();
        assertThat(page.last()).isTrue();
    }

    @Test
    void returnsRequestedPageMetadataAndSkipsTranslationLookupForEmptyPage() {
        PageRequest pageRequest = PageRequest.of(2, 5);
        when(articleRepository.findAllFiltered(null, "", pageRequest))
                .thenReturn(new PageImpl<>(List.of(), pageRequest, 14));

        ArticlePageResponse page = articleService.getAll(2, 5, null, null);

        assertThat(page.content()).isEmpty();
        assertThat(page.page()).isEqualTo(2);
        assertThat(page.size()).isEqualTo(5);
        assertThat(page.totalElements()).isEqualTo(14);
        assertThat(page.totalPages()).isEqualTo(3);
        assertThat(page.first()).isFalse();
        assertThat(page.last()).isTrue();
        verifyNoInteractions(articleTranslationRepository);
    }

    @Test
    void pagedListDoesNotExposeMissingPendingOrFailedTranslations() {
        Article missing = article(1L, SourceLanguage.EN);
        Article pendingArticle = article(2L, SourceLanguage.EN);
        Article failedArticle = article(3L, SourceLanguage.EN);
        ArticleTranslation pending = translation(pendingArticle, TranslationStatus.PENDING);
        ArticleTranslation failed = translation(failedArticle, TranslationStatus.FAILED);
        PageRequest pageRequest = PageRequest.of(0, 20);
        when(articleRepository.findAllFiltered(null, "", pageRequest)).thenReturn(new PageImpl<>(
                List.of(missing, pendingArticle, failedArticle),
                pageRequest,
                3
        ));
        when(articleTranslationRepository.findAllByArticleIdInAndLanguageAndStatus(
                List.of(1L, 2L, 3L),
                TranslationLanguage.ZH_CN,
                TranslationStatus.SUCCESS
        )).thenReturn(List.of(pending, failed));

        ArticlePageResponse page = articleService.getAll(0, 20, null, null);

        assertThat(page.content())
                .extracting(ArticleResponse::translation)
                .containsOnlyNulls();
        verify(articleTranslationRepository).findAllByArticleIdInAndLanguageAndStatus(
                List.of(1L, 2L, 3L),
                TranslationLanguage.ZH_CN,
                TranslationStatus.SUCCESS
        );
    }

    @Test
    void treatsBlankKeywordAsNoKeywordFilter() {
        PageRequest pageRequest = PageRequest.of(0, 20);
        when(articleRepository.findAllFiltered(3L, "", pageRequest))
                .thenReturn(new PageImpl<>(List.of(), pageRequest, 0));

        ArticlePageResponse page = articleService.getAll(0, 20, 3L, "   ");

        assertThat(page.content()).isEmpty();
        verify(articleRepository).findAllFiltered(3L, "", pageRequest);
    }

    @Test
    void normalizesKeywordBeforeDatabaseFiltering() {
        PageRequest pageRequest = PageRequest.of(0, 20);
        when(articleRepository.findAllFiltered(null, "battery", pageRequest))
                .thenReturn(new PageImpl<>(List.of(), pageRequest, 0));

        articleService.getAll(0, 20, null, "  BaTtErY  ");

        verify(articleRepository).findAllFiltered(null, "battery", pageRequest);
    }

    @Test
    void returnsArticleByIdWithSuccessfulTranslation() {
        Article article = article(SourceLanguage.EN);
        ArticleTranslation translation = translation(article, TranslationStatus.SUCCESS);
        translation.setTitle("中文标题");
        when(articleRepository.findById(1L)).thenReturn(Optional.of(article));
        when(articleTranslationRepository.findByArticleIdAndLanguageAndStatus(
                1L,
                TranslationLanguage.ZH_CN,
                TranslationStatus.SUCCESS
        )).thenReturn(Optional.of(translation));

        ArticleResponse response = articleService.getById(1L);

        assertThat(response.original().language()).isEqualTo(SourceLanguage.EN);
        assertThat(response.translation()).isEqualTo(new ArticleTranslationResponse(
                TranslationLanguage.ZH_CN,
                "中文标题",
                null
        ));
    }

    @Test
    void returnsNullTranslationWhenNoSuccessfulTranslationExists() {
        Article article = article(SourceLanguage.EN);
        when(articleRepository.findById(1L)).thenReturn(Optional.of(article));
        when(articleTranslationRepository.findByArticleIdAndLanguageAndStatus(
                1L,
                TranslationLanguage.ZH_CN,
                TranslationStatus.SUCCESS
        )).thenReturn(Optional.empty());

        ArticleResponse response = articleService.getById(1L);

        assertThat(response.translation()).isNull();
    }

    @Test
    void doesNotExposeFailedTranslation() {
        Article article = article(SourceLanguage.EN);
        ArticleTranslation failed = translation(article, TranslationStatus.FAILED);
        failed.setTitle("Stale translated title");
        when(articleRepository.findById(1L)).thenReturn(Optional.of(article));
        when(articleTranslationRepository.findByArticleIdAndLanguageAndStatus(
                1L,
                TranslationLanguage.ZH_CN,
                TranslationStatus.SUCCESS
        )).thenReturn(Optional.of(failed));

        ArticleResponse response = articleService.getById(1L);

        assertThat(response.translation()).isNull();
    }

    @Test
    void doesNotExposePendingTranslation() {
        Article article = article(SourceLanguage.EN);
        ArticleTranslation pending = translation(article, TranslationStatus.PENDING);
        pending.setTitle("Partial translated title");
        when(articleRepository.findById(1L)).thenReturn(Optional.of(article));
        when(articleTranslationRepository.findByArticleIdAndLanguageAndStatus(
                1L,
                TranslationLanguage.ZH_CN,
                TranslationStatus.SUCCESS
        )).thenReturn(Optional.of(pending));

        ArticleResponse response = articleService.getById(1L);

        assertThat(response.translation()).isNull();
    }

    @Test
    void reportsChineseSourceLanguageFromSource() {
        Article article = article(SourceLanguage.ZH_CN);
        when(articleRepository.findById(1L)).thenReturn(Optional.of(article));
        when(articleTranslationRepository.findByArticleIdAndLanguageAndStatus(
                1L,
                TranslationLanguage.ZH_CN,
                TranslationStatus.SUCCESS
        )).thenReturn(Optional.empty());

        ArticleResponse response = articleService.getById(1L);

        assertThat(response.original().language()).isEqualTo(SourceLanguage.ZH_CN);
        assertThat(response.translation()).isNull();
    }

    @Test
    void throwsWhenArticleDoesNotExist() {
        when(articleRepository.findById(42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> articleService.getById(42L))
                .isInstanceOf(ArticleNotFoundException.class)
                .hasMessage("Article with id 42 was not found");
        verifyNoInteractions(articleTranslationRepository);
    }

    @Test
    void throwsWhenRequestedSourceDoesNotExist() {
        CreateArticleRequest request = request();
        when(sourceRepository.findById(request.sourceId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> articleService.create(request))
                .isInstanceOf(SourceNotFoundException.class)
                .hasMessage("Source with id 7 was not found");
        verify(articleRepository, never()).saveAndFlush(any(Article.class));
    }

    @Test
    void createsArticleWithExistingFlatResponseAndBackendCollectionTime() {
        CreateArticleRequest request = request();
        Source source = createSource();
        when(sourceRepository.findById(request.sourceId())).thenReturn(Optional.of(source));
        when(articleRepository.existsByUrl(request.url())).thenReturn(false);
        when(articleRepository.saveAndFlush(any(Article.class))).thenAnswer(invocation -> {
            Article article = invocation.getArgument(0);
            article.onCreate();
            return article;
        });
        Instant beforeCreate = Instant.now();

        CreateArticleResponse response = articleService.create(request);

        Instant afterCreate = Instant.now();
        ArgumentCaptor<Article> articleCaptor = ArgumentCaptor.forClass(Article.class);
        verify(articleRepository).saveAndFlush(articleCaptor.capture());
        Article saved = articleCaptor.getValue();
        assertThat(saved.getSource()).isSameAs(source);
        assertThat(saved.getCollectedAt()).isBetween(beforeCreate, afterCreate);
        assertThat(response.sourceId()).isEqualTo(7L);
        assertThat(response.sourceName()).isEqualTo("Energy Storage News");
        assertThat(response.collectedAt()).isEqualTo(saved.getCollectedAt());
        assertThat(response.description()).isEqualTo(request.description());
        assertThat(response.content()).isEqualTo(request.content());
        assertThat(response.publishedAt()).isEqualTo(request.publishedAt());
        verifyNoInteractions(articleTranslationRepository);
    }

    @Test
    void rejectsKnownDuplicateUrlBeforeSaving() {
        CreateArticleRequest request = request();
        when(sourceRepository.findById(request.sourceId()))
                .thenReturn(Optional.of(mock(Source.class)));
        when(articleRepository.existsByUrl(request.url())).thenReturn(true);

        assertThatThrownBy(() -> articleService.create(request))
                .isInstanceOf(DuplicateArticleUrlException.class)
                .hasMessage("An article with URL 'https://example.com/articles/new' already exists");
        verify(articleRepository, never()).saveAndFlush(any(Article.class));
    }

    @Test
    void translatesDatabaseConstraintViolationIntoDuplicateUrlError() {
        CreateArticleRequest request = request();
        when(sourceRepository.findById(request.sourceId()))
                .thenReturn(Optional.of(mock(Source.class)));
        when(articleRepository.existsByUrl(request.url())).thenReturn(false);
        DataIntegrityViolationException databaseException =
                new DataIntegrityViolationException("unique constraint");
        when(articleRepository.saveAndFlush(any(Article.class))).thenThrow(databaseException);

        assertThatThrownBy(() -> articleService.create(request))
                .isInstanceOf(DuplicateArticleUrlException.class)
                .hasMessage("An article with URL 'https://example.com/articles/new' already exists")
                .hasCause(databaseException);
    }

    private static CreateArticleRequest request() {
        return new CreateArticleRequest(
                "New article",
                "https://example.com/articles/new",
                "Article summary",
                "Article content",
                Instant.parse("2026-08-18T12:00:00Z"),
                7L
        );
    }

    private static Article article(SourceLanguage language) {
        return article(1L, language);
    }

    private static Article article(long id, SourceLanguage language) {
        Article article = new Article(
                "Stored article",
                "https://example.com/articles/stored-" + id,
                source(language),
                Instant.parse("2026-08-19T06:00:00Z")
        );
        article.setDescription("Original summary");
        article.setContent("Original content");
        article.setPublishedAt(Instant.parse("2026-08-18T12:00:00Z"));
        article.onCreate();
        ReflectionTestUtils.setField(article, "id", id);
        return article;
    }

    private static Source source(SourceLanguage language) {
        Source source = mock(Source.class);
        when(source.getId()).thenReturn(7L);
        when(source.getName()).thenReturn("Energy Storage News");
        when(source.getLanguage()).thenReturn(language);
        return source;
    }

    private static Source createSource() {
        Source source = mock(Source.class);
        when(source.getId()).thenReturn(7L);
        when(source.getName()).thenReturn("Energy Storage News");
        return source;
    }

    private static ArticleTranslation translation(Article article, TranslationStatus status) {
        ArticleTranslation translation = new ArticleTranslation(article, TranslationLanguage.ZH_CN);
        translation.setStatus(status);
        return translation;
    }
}
