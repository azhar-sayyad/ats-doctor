package com.atsdoctor.backend.api.tailor;

import com.atsdoctor.backend.application.tailoring.TailoringConflictException;
import com.atsdoctor.backend.application.tailoring.TailoringNotFoundException;
import com.atsdoctor.backend.application.tailoring.TailoringValidationException;
import com.atsdoctor.backend.infrastructure.export.ResumeTemplateCatalog.UnknownResumeTemplateException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * RFC 7807 ProblemDetail mapping for tailoring endpoints (07-api-contract,
 * PRD §8). Ordered before the global fallback so domain exceptions always win.
 */
@RestControllerAdvice(assignableTypes = {TailoringController.class, TailoringExportController.class})
@Order(Ordered.HIGHEST_PRECEDENCE)
@ConditionalOnProperty(name = "ats.doctor.persistence.enabled", havingValue = "true")
public class TailoringExceptionHandler {

    @ExceptionHandler(TailoringNotFoundException.class)
    public ProblemDetail notFound(TailoringNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "Tailored resume not found", ex.getMessage());
    }

    @ExceptionHandler(TailoringValidationException.class)
    public ProblemDetail badData(TailoringValidationException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid tailoring request", ex.getMessage());
    }

    @ExceptionHandler(UnknownResumeTemplateException.class)
    public ProblemDetail unknownTemplate(UnknownResumeTemplateException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Unknown resume template", ex.getMessage());
    }

    @ExceptionHandler(TailoringConflictException.class)
    public ProblemDetail conflict(TailoringConflictException ex) {
        return problem(HttpStatus.CONFLICT, "Tailoring not ready", ex.getMessage());
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return problem;
    }
}