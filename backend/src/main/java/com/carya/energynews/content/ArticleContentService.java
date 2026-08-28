package com.carya.energynews.content;

import com.carya.energynews.article.Article;
import com.carya.energynews.article.ArticleRepository;
import org.springframework.stereotype.Service;

@Service
public class ArticleContentService {

    private final ArticleContentFetcher articleContentFetcher;
    private final ArticleRepository articleRepository;

    public ArticleContentService(
            ArticleContentFetcher articleContentFetcher,
            ArticleRepository articleRepository
    ) {
        this.articleContentFetcher = articleContentFetcher;
        this.articleRepository = articleRepository;
    }

    public Article enrichContent(Article article) {
        if (article.getContent() != null && !article.getContent().isBlank()) {
            return article;
        }

        String content = articleContentFetcher.fetchContent(article);
        if (content == null || content.isBlank()) {
            throw new ArticleContentFetchException("Content fetcher returned no usable article content");
        }

        article.setContent(content);
        Article savedArticle = articleRepository.saveAndFlush(article);
        return articleRepository.findWithSourceById(savedArticle.getId())
                .orElseThrow(() -> new IllegalStateException(
                        "Saved Article could not be reloaded for post-processing"
                ));
    }
}
