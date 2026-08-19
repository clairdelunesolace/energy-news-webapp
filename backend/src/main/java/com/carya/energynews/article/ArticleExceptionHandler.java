package com.carya.energynews.article;

import com.carya.energynews.source.SourceNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice(assignableTypes = ArticleController.class)
public class ArticleExceptionHandler {

    @ExceptionHandler(ArticleNotFoundException.class)
    public ProblemDetail handleArticleNotFound(ArticleNotFoundException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
        problem.setTitle("Article not found");
        return problem;
    }

    @ExceptionHandler(SourceNotFoundException.class)
    public ProblemDetail handleSourceNotFound(SourceNotFoundException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
        problem.setTitle("Source not found");
        return problem;
    }

    @ExceptionHandler(DuplicateArticleUrlException.class)
    public ProblemDetail handleDuplicateUrl(DuplicateArticleUrlException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
        problem.setTitle("Duplicate article URL");
        return problem;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException exception) {
        Map<String, String> errors = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors()
                .forEach(error -> errors.putIfAbsent(error.getField(), error.getDefaultMessage()));

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "The request contains invalid fields"
        );
        problem.setTitle("Validation failed");
        problem.setProperty("errors", errors);
        return problem;
    }
}
