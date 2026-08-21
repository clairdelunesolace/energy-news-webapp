package com.carya.energynews.translation;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(assignableTypes = ArticleContentTranslationBackfillController.class)
public class ArticleContentTranslationBackfillExceptionHandler {

    @ExceptionHandler(InvalidArticleContentTranslationBackfillLimitException.class)
    public ProblemDetail handleInvalidLimit(
            InvalidArticleContentTranslationBackfillLimitException exception
    ) {
        return invalidLimitProblem(exception.getMessage());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail handleInvalidLimitType() {
        return invalidLimitProblem(
                "Article content translation backfill limit must be an integer between 1 and 10"
        );
    }

    private ProblemDetail invalidLimitProblem(String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
        problem.setTitle("Invalid article content translation backfill limit");
        return problem;
    }
}
