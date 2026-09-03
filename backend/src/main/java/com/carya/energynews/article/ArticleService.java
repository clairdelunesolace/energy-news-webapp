package com.carya.energynews.article;

import com.carya.energynews.source.Source;
import com.carya.energynews.source.SourceNotFoundException;
import com.carya.energynews.source.SourceRepository;
import com.carya.energynews.translation.ArticleTranslation;
import com.carya.energynews.translation.ArticleTranslationRepository;
import com.carya.energynews.translation.ContentTranslationStatus;
import com.carya.energynews.translation.TranslationLanguage;
import com.carya.energynews.translation.TranslationStatus;
import com.carya.energynews.watchlist.KeywordRepository;
import com.carya.energynews.watchlist.WatchlistKeywordMatcher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class ArticleService {

    private final ArticleRepository articleRepository;
    private final SourceRepository sourceRepository;
    private final ArticleTranslationRepository articleTranslationRepository;
    private final KeywordRepository keywordRepository;
    private final WatchlistKeywordMatcher watchlistKeywordMatcher;

    public ArticleService(
            ArticleRepository articleRepository,
            SourceRepository sourceRepository,
            ArticleTranslationRepository articleTranslationRepository,
            KeywordRepository keywordRepository,
            WatchlistKeywordMatcher watchlistKeywordMatcher
    ) {
        this.articleRepository = articleRepository;
        this.sourceRepository = sourceRepository;
        this.articleTranslationRepository = articleTranslationRepository;
        this.keywordRepository = keywordRepository;
        this.watchlistKeywordMatcher = watchlistKeywordMatcher;
    }

    @Transactional(readOnly = true)
    public ArticlePageResponse getAll(int page, int size, Long sourceId, String keyword, Long keywordId) {
        String normalizedKeyword = normalizeKeyword(keyword);
        Page<Article> articlePage = articleRepository.findAllFiltered(
                sourceId,
                normalizedKeyword,
                keywordId,
                PageRequest.of(page, size)
        );
        List<Article> articles = articlePage.getContent();
        Map<Long, ArticleTranslation> translations = loadSuccessfulTranslations(articles);
        List<String> keywords = articles.isEmpty() ? List.of() : keywordRepository.findEnabledKeywordTexts();
        List<ArticleResponse> content = articles.stream()
                .map(article -> toResponse(article, translations.get(article.getId()), keywords))
                .toList();
        return new ArticlePageResponse(
                content,
                articlePage.getNumber(),
                articlePage.getSize(),
                articlePage.getTotalElements(),
                articlePage.getTotalPages(),
                articlePage.isFirst(),
                articlePage.isLast()
        );
    }

    private static String normalizeKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return "";
        }
        return keyword.trim().toLowerCase(Locale.ROOT);
    }

    @Transactional(readOnly = true)
    public ArticleResponse getById(Long id) {
        Article article = articleRepository.findById(id)
                .orElseThrow(() -> new ArticleNotFoundException(id));
        ArticleTranslation translation = articleTranslationRepository
                .findByArticleIdAndLanguageAndStatus(
                        id,
                        TranslationLanguage.ZH_CN,
                        TranslationStatus.SUCCESS
                )
                .orElse(null);
        return toResponse(article, translation, keywordRepository.findEnabledKeywordTexts());
    }

    @Transactional
    public CreateArticleResponse create(CreateArticleRequest request) {
        Source source = sourceRepository.findById(request.sourceId())
                .orElseThrow(() -> new SourceNotFoundException(request.sourceId()));

        if (articleRepository.existsByUrl(request.url())) {
            throw new DuplicateArticleUrlException(request.url());
        }

        Article article = new Article(request.title(), request.url(), source, Instant.now());
        article.setDescription(request.description());
        article.setContent(request.content());
        article.setPublishedAt(request.publishedAt());

        try {
            return toCreateResponse(articleRepository.saveAndFlush(article));
        } catch (DataIntegrityViolationException exception) {
            throw new DuplicateArticleUrlException(request.url(), exception);
        }
    }

    private Map<Long, ArticleTranslation> loadSuccessfulTranslations(List<Article> articles) {
        if (articles.isEmpty()) {
            return Map.of();
        }

        List<Long> articleIds = articles.stream()
                .map(Article::getId)
                .toList();
        List<ArticleTranslation> translations = articleTranslationRepository
                .findAllByArticleIdInAndLanguageAndStatus(
                        articleIds,
                        TranslationLanguage.ZH_CN,
                        TranslationStatus.SUCCESS
                );

        Map<Long, ArticleTranslation> translationsByArticleId = new HashMap<>();
        for (ArticleTranslation translation : translations) {
            if (translation.getStatus() == TranslationStatus.SUCCESS) {
                translationsByArticleId.put(translation.getArticle().getId(), translation);
            }
        }
        return translationsByArticleId;
    }

    private ArticleResponse toResponse(
            Article article, ArticleTranslation translation, List<String> keywords
    ) {
        Source source = article.getSource();
        return new ArticleResponse(
                article.getId(),
                new ArticleSourceResponse(source.getId(), source.getName()),
                article.getUrl(),
                article.getPublishedAt(),
                article.getCollectedAt(),
                new ArticleOriginalResponse(
                        source.getLanguage(),
                        article.getTitle(),
                        article.getDescription(),
                        article.getContent()
                ),
                toTranslationResponse(translation),
                article.getCreatedAt(),
                article.getUpdatedAt(),
                watchlistKeywordMatcher.matchTags(article.getTitle(), article.getDescription(), keywords)
        );
    }

    private static ArticleTranslationResponse toTranslationResponse(ArticleTranslation translation) {
        if (translation == null || translation.getStatus() != TranslationStatus.SUCCESS) {
            return null;
        }
        return new ArticleTranslationResponse(
                translation.getLanguage(),
                translation.getTitle(),
                translation.getDescription(),
                translation.getContentStatus() == ContentTranslationStatus.SUCCESS
                        && translation.getContent() != null
                        && !translation.getContent().isBlank()
                        ? translation.getContent()
                        : null
        );
    }

    private static CreateArticleResponse toCreateResponse(Article article) {
        Source source = article.getSource();
        return new CreateArticleResponse(
                article.getId(),
                article.getTitle(),
                article.getUrl(),
                article.getDescription(),
                article.getContent(),
                article.getPublishedAt(),
                article.getCollectedAt(),
                source.getId(),
                source.getName(),
                article.getCreatedAt(),
                article.getUpdatedAt()
        );
    }
}
