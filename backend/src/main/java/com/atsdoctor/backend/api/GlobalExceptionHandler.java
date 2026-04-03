package com.atsdoctor.backend.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Fallback RFC 7807 advice (TASK-089, PRD §8). Extends
 * {@link ResponseEntityExceptionHandler} so Spring's standard MVC exceptions
 * (malformed JSON, missing params, method-not-allowed, oversized uploads, …)
 * keep their normal status codes as ProblemDetail, while the catch-all maps
 * any otherwise-unhandled exception to a stable {@code 500} ProblemDetail
 * without leaking stack details. Per-controller advice (assignableTypes)
 * stays more specific and wins for the domain exceptions it declares.
 */
@RestControllerAdvice
@ConditionalOnProperty(name = "ats.doctor.persistence.enabled", havingValue = "true")
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(Exception.class)
    public ProblemDetail unexpected(Exception ex) {
        log.warn("Unhandled exception in controller layer", ex);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
        problem.setTitle("Internal server error");
        return problem;
    }
}
