package com.carya.energynews.article;

import com.carya.energynews.collection.CollectedArticle;
import com.carya.energynews.source.Source;
import com.carya.energynews.source.SourceNotFoundException;
import com.carya.energynews.source.SourceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class ArticleIngestionService {

    private final ArticleRepository articleRepository;
    private final SourceRepository sourceRepository;

    public ArticleIngestionService(ArticleRepository articleRepository, SourceRepository sourceRepository) {
        this.articleRepository = articleRepository;
        this.sourceRepository = sourceRepository;
    }

    @Transactional
    public ArticleIngestionResult ingest(CollectedArticle collectedArticle) {
        Source source = sourceRepository.findById(collectedArticle.sourceId())
                .orElseThrow(() -> new SourceNotFoundException(collectedArticle.sourceId()));

        if (articleRepository.existsByUrl(collectedArticle.url())) {
            return ArticleIngestionResult.DUPLICATE;
        }

        Article article = new Article(
                collectedArticle.title(),
                collectedArticle.url(),
                source,
                Instant.now()
        );
        article.setDescription(collectedArticle.description());
        article.setContent(collectedArticle.content());
        article.setPublishedAt(collectedArticle.publishedAt());
        articleRepository.save(article);

        return ArticleIngestionResult.SAVED;
    }

    @Transactional
    public List<ArticleIngestionResult> ingestAll(List<CollectedArticle> collectedArticles) {
        return collectedArticles.stream()
                .map(this::ingest)
                .toList();
    }
}
