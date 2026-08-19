package com.carya.energynews.article;

public class DuplicateArticleUrlException extends RuntimeException {

    public DuplicateArticleUrlException(String url) {
        super("An article with URL '" + url + "' already exists");
    }

    public DuplicateArticleUrlException(String url, Throwable cause) {
        super("An article with URL '" + url + "' already exists", cause);
    }
}
