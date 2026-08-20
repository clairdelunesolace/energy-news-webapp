package com.carya.energynews.article;

public class InvalidArticlePageException extends RuntimeException {

    public InvalidArticlePageException() {
        super("Page must be at least 0 and size must be between 1 and 100");
    }
}
