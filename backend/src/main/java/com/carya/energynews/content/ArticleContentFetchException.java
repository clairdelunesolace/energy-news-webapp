package com.carya.energynews.content;

public class ArticleContentFetchException extends RuntimeException {

    public ArticleContentFetchException(String message) {
        super(message);
    }

    public ArticleContentFetchException(String message, Throwable cause) {
        super(message, cause);
    }
}
