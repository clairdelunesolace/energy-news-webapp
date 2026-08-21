package com.carya.energynews.content;

import com.carya.energynews.article.Article;
import com.carya.energynews.article.ArticleRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ArticleContentBackfillService {

    static final int MIN_LIMIT = 1;
    static final int MAX_LIMIT = 20;

    private final ArticleRepository articleRepository;
    private final ArticleContentService articleContentService;

    public ArticleContentBackfillService(
            ArticleRepository articleRepository,
            ArticleContentService articleContentService
    ) {
        this.articleRepository = articleRepository;
        this.articleContentService = articleContentService;
    }

    public ArticleContentBackfillResult backfill(int limit) {
        if (limit < MIN_LIMIT || limit > MAX_LIMIT) {
            throw new InvalidArticleContentBackfillLimitException();
        }

        List<Article> candidates = articleRepository.findContentBackfillCandidates(
                PageRequest.of(0, limit)
        );

        int fetched = 0;
        int failed = 0;
        for (Article article : candidates) {
            try {
                articleContentService.enrichContent(article);
                fetched++;
            } catch (ArticleContentFetchException exception) {
                failed++;
            }
        }

        return new ArticleContentBackfillResult(candidates.size(), fetched, failed);
    }
}
