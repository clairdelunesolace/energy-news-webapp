package com.carya.energynews.dailybriefanalysis;

import com.carya.energynews.dailybrief.DailyBriefNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = DailyBriefAnalysisController.class)
public class DailyBriefAnalysisExceptionHandler {

    @ExceptionHandler(DailyBriefNotFoundException.class)
    public ProblemDetail handleDailyBriefNotFound(DailyBriefNotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, "Daily brief not found", exception.getMessage());
    }

    @ExceptionHandler(DailyBriefAnalysisNotFoundException.class)
    public ProblemDetail handleAnalysisNotFound(DailyBriefAnalysisNotFoundException exception) {
        return problem(
                HttpStatus.NOT_FOUND,
                "Daily brief AI analysis not found",
                exception.getMessage()
        );
    }

    @ExceptionHandler(DailyBriefEmptyAnalysisException.class)
    public ProblemDetail handleEmptyBrief(DailyBriefEmptyAnalysisException exception) {
        return problem(
                HttpStatus.CONFLICT,
                "Daily brief has no evidence to analyze",
                exception.getMessage()
        );
    }

    @ExceptionHandler(DailyBriefAiProviderUnavailableException.class)
    public ProblemDetail handleProviderUnavailable(
            DailyBriefAiProviderUnavailableException exception
    ) {
        return problem(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Daily brief AI provider unavailable",
                exception.getMessage()
        );
    }

    @ExceptionHandler(DailyBriefAnalysisStaleSnapshotException.class)
    public ProblemDetail handleStaleSnapshot(DailyBriefAnalysisStaleSnapshotException exception) {
        return problem(
                HttpStatus.CONFLICT,
                "Daily brief changed during AI generation",
                exception.getMessage()
        );
    }

    @ExceptionHandler(DailyBriefAiValidationException.class)
    public ProblemDetail handleInvalidAiResult(DailyBriefAiValidationException exception) {
        return problem(
                HttpStatus.BAD_GATEWAY,
                "Daily brief AI response was invalid",
                exception.getMessage()
        );
    }

    @ExceptionHandler(DailyBriefAiException.class)
    public ProblemDetail handleProviderFailure(DailyBriefAiException exception) {
        HttpStatus status = switch (exception.getFailure()) {
            case RATE_LIMITED -> HttpStatus.SERVICE_UNAVAILABLE;
            case TIMEOUT -> HttpStatus.GATEWAY_TIMEOUT;
            case AUTHENTICATION, AUTHORIZATION, INVALID_REQUEST, UPSTREAM, MALFORMED_RESPONSE ->
                    HttpStatus.BAD_GATEWAY;
        };
        return problem(status, "Daily brief AI provider failed", exception.getMessage());
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return problem;
    }
}
