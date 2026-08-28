package com.carya.energynews.dailybrief;

import com.carya.energynews.watchlist.WatchlistNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice(assignableTypes = DailyBriefController.class)
public class DailyBriefExceptionHandler {

    @ExceptionHandler(WatchlistNotFoundException.class)
    public ProblemDetail handleWatchlistNotFound(WatchlistNotFoundException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND,
                exception.getMessage()
        );
        problem.setTitle("Watchlist not found");
        return problem;
    }

    @ExceptionHandler(DailyBriefNotFoundException.class)
    public ProblemDetail handleDailyBriefNotFound(DailyBriefNotFoundException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND,
                exception.getMessage()
        );
        problem.setTitle("Daily brief not found");
        return problem;
    }

    @ExceptionHandler(DailyBriefWatchlistDisabledException.class)
    public ProblemDetail handleDisabledWatchlist(DailyBriefWatchlistDisabledException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT,
                exception.getMessage()
        );
        problem.setTitle("Watchlist is disabled");
        return problem;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleInvalidRequest(IllegalArgumentException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                exception.getMessage()
        );
        problem.setTitle("Invalid daily brief request");
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
