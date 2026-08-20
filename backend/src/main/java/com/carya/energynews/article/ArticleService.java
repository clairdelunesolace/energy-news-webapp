package com.carya.energynews.article;

import com.carya.energynews.source.Source;
import com.carya.energynews.source.SourceNotFoundException;
import com.carya.energynews.source.SourceRepository;
import com.carya.energynews.translation.ArticleTranslation;
import com.carya.energynews.translation.ArticleTranslationRepository;
import com.carya.energynews.translation.TranslationLanguage;
import com.carya.energynews.translation.TranslationStatus;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ArticleService {

    private final ArticleRepository articleRepository;
    private final SourceRepository sourceRepository;
    private final ArticleTranslationRepository articleTranslationRepository;

    public ArticleService(
            ArticleRepository articleRepository,
            SourceRepository sourceRepository,
            ArticleTranslationRepository articleTranslationRepository
    ) {
        this.articleRepository = articleRepository;
        this.sourceRepository = sourceRepository;
        this.articleTranslationRepository = articleTranslationRepository;
    }

    @Transactional(readOnly = true)
    public List<ArticleResponse> getAll() {
        List<Article> articles = articleRepository.findAll();
        Map<Long, ArticleTranslation> translations = loadSuccessfulTranslations(articles);
        return articles.stream()
                .map(article -> toResponse(article, translations.get(article.getId())))
                .toList();
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
        return toResponse(article, translation);
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

    private static ArticleResponse toResponse(Article article, ArticleTranslation translation) {
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
                article.getUpdatedAt()
        );
    }

    private static ArticleTranslationResponse toTranslationResponse(ArticleTranslation translation) {
        if (translation == null || translation.getStatus() != TranslationStatus.SUCCESS) {
            return null;
        }
        return new ArticleTranslationResponse(
                translation.getLanguage(),
                translation.getTitle(),
                translation.getDescription()
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
