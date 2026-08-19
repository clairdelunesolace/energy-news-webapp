package com.carya.energynews.article;

public class ArticleNotFoundException extends RuntimeException {

    public ArticleNotFoundException(Long id) {
        super("Article with id " + id + " was not found");
    }
}
