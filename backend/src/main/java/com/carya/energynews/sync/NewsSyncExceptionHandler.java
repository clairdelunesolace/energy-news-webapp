package com.carya.energynews.sync;

import com.carya.energynews.collection.NewsCollectionException;
import com.carya.energynews.source.SourceNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = NewsSyncController.class)
public class NewsSyncExceptionHandler {

    @ExceptionHandler(SourceNotFoundException.class)
    public ProblemDetail handleSourceNotFound(SourceNotFoundException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
        problem.setTitle("Source not found");
        return problem;
    }

    @ExceptionHandler(NewsCollectionException.class)
    public ProblemDetail handleCollectionFailure(NewsCollectionException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, exception.getMessage());
        problem.setTitle("News collection failed");
        return problem;
    }
}
