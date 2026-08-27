package com.carya.energynews.discovery;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = NewsDiscoveryPreviewController.class)
public class NewsDiscoveryExceptionHandler {

    @ExceptionHandler(NewsDiscoveryProviderUnavailableException.class)
    public ProblemDetail handleProviderUnavailable(
            NewsDiscoveryProviderUnavailableException exception
    ) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.SERVICE_UNAVAILABLE,
                exception.getMessage()
        );
        problem.setTitle("Service Unavailable");
        return problem;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleInvalidQuery(IllegalArgumentException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                exception.getMessage()
        );
        problem.setTitle("Invalid discovery query");
        return problem;
    }

    @ExceptionHandler(NewsDiscoveryException.class)
    public ProblemDetail handleDiscoveryFailure(NewsDiscoveryException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_GATEWAY,
                exception.getMessage()
        );
        problem.setTitle("News discovery failed");
        return problem;
    }
}

class NewsDiscoveryProviderUnavailableException extends RuntimeException {

    NewsDiscoveryProviderUnavailableException(String message) {
        super(message);
    }
}
