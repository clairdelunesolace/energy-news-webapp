package com.carya.energynews.content;

import com.carya.energynews.article.Article;

public interface ArticleContentFetcher {

    String fetchContent(Article article);
}
