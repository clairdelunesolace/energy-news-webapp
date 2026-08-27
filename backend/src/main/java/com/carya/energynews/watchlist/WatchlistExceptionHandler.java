package com.carya.energynews.watchlist;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice(assignableTypes = {WatchlistController.class, KeywordController.class})
public class WatchlistExceptionHandler {

    @ExceptionHandler(WatchlistNotFoundException.class)
    public ProblemDetail handleWatchlistNotFound(WatchlistNotFoundException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
        problem.setTitle("Watchlist not found");
        return problem;
    }

    @ExceptionHandler(KeywordNotFoundException.class)
    public ProblemDetail handleKeywordNotFound(KeywordNotFoundException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
        problem.setTitle("Keyword not found");
        return problem;
    }

    @ExceptionHandler(DuplicateWatchlistNameException.class)
    public ProblemDetail handleDuplicateWatchlist(DuplicateWatchlistNameException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
        problem.setTitle("Duplicate watchlist name");
        return problem;
    }

    @ExceptionHandler(DuplicateKeywordException.class)
    public ProblemDetail handleDuplicateKeyword(DuplicateKeywordException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
        problem.setTitle("Duplicate keyword");
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
