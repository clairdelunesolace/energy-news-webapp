package com.carya.energynews.content;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(assignableTypes = ArticleContentBackfillController.class)
public class ArticleContentBackfillExceptionHandler {

    @ExceptionHandler(InvalidArticleContentBackfillLimitException.class)
    public ProblemDetail handleInvalidLimit(InvalidArticleContentBackfillLimitException exception) {
        return invalidLimitProblem(exception.getMessage());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail handleInvalidLimitType() {
        return invalidLimitProblem(
                "Article content backfill limit must be an integer between 1 and 20"
        );
    }

    private ProblemDetail invalidLimitProblem(String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                detail
        );
        problem.setTitle("Invalid article content backfill limit");
        return problem;
    }
}
