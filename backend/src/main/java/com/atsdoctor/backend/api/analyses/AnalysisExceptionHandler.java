package com.atsdoctor.backend.api.analyses;

import com.atsdoctor.backend.application.analysis.AnalysisNotReadyException;
import com.atsdoctor.backend.application.analysis.AnalysisNotFoundException;
import com.atsdoctor.backend.application.analysis.AnalysisValidationException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * RFC 7807 ProblemDetail mapping for analysis endpoints (07-api-contract,
 * PRD §8). Ordered before the global fallback so domain exceptions always win.
 */
@RestControllerAdvice(assignableTypes = AnalysisController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
@ConditionalOnProperty(name = "ats.doctor.persistence.enabled", havingValue = "true")
public class AnalysisExceptionHandler {

    @ExceptionHandler(AnalysisNotFoundException.class)
    public ProblemDetail notFound(AnalysisNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "Analysis not found", ex.getMessage());
    }

    @ExceptionHandler(AnalysisValidationException.class)
    public ProblemDetail badData(AnalysisValidationException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid analysis request", ex.getMessage());
    }

    @ExceptionHandler(AnalysisNotReadyException.class)
    public ProblemDetail conflict(AnalysisNotReadyException ex) {
        return problem(HttpStatus.CONFLICT, "Analysis not ready", ex.getMessage());
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return problem;
    }
}
