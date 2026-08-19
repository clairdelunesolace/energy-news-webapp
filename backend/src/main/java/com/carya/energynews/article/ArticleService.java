package com.carya.energynews.article;

import com.carya.energynews.source.Source;
import com.carya.energynews.source.SourceNotFoundException;
import com.carya.energynews.source.SourceRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class ArticleService {

    private final ArticleRepository articleRepository;
    private final SourceRepository sourceRepository;

    public ArticleService(ArticleRepository articleRepository, SourceRepository sourceRepository) {
        this.articleRepository = articleRepository;
        this.sourceRepository = sourceRepository;
    }

    @Transactional(readOnly = true)
    public List<ArticleResponse> getAll() {
        return articleRepository.findAll().stream()
                .map(ArticleService::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ArticleResponse getById(Long id) {
        Article article = articleRepository.findById(id)
                .orElseThrow(() -> new ArticleNotFoundException(id));
        return toResponse(article);
    }

    @Transactional
    public ArticleResponse create(CreateArticleRequest request) {
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
            return toResponse(articleRepository.saveAndFlush(article));
        } catch (DataIntegrityViolationException exception) {
            throw new DuplicateArticleUrlException(request.url(), exception);
        }
    }

    private static ArticleResponse toResponse(Article article) {
        Source source = article.getSource();
        return new ArticleResponse(
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
