package com.carya.energynews.filter;

import com.carya.energynews.collection.CollectedArticle;

public interface ArticleFilter {

    FilterResult evaluate(CollectedArticle article);
}
