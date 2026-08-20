package com.carya.energynews.translation;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(assignableTypes = TranslationBackfillController.class)
public class TranslationBackfillExceptionHandler {

    @ExceptionHandler(InvalidTranslationBackfillLimitException.class)
    public ProblemDetail handleInvalidLimit(InvalidTranslationBackfillLimitException exception) {
        return invalidLimitProblem(exception.getMessage());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail handleInvalidLimitType() {
        return invalidLimitProblem("Translation backfill limit must be an integer between 1 and 100");
    }

    private ProblemDetail invalidLimitProblem(String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                detail
        );
        problem.setTitle("Invalid translation backfill limit");
        return problem;
    }
}
