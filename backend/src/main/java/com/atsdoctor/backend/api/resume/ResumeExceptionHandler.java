package com.atsdoctor.backend.api.resume;

import com.atsdoctor.backend.application.resume.ResumeNotFoundException;
import com.atsdoctor.backend.application.resume.ResumeNotReadyException;
import com.atsdoctor.backend.application.resume.ResumeStorageException;
import com.atsdoctor.backend.application.resume.ResumeValidationException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * RFC 7807 ProblemDetail mapping for resume endpoints (07-api-contract, PRD §8).
 */
@RestControllerAdvice(assignableTypes = ResumeController.class)
@ConditionalOnProperty(name = "ats.doctor.persistence.enabled", havingValue = "true")
public class ResumeExceptionHandler {

    @ExceptionHandler(ResumeNotFoundException.class)
    public ProblemDetail notFound(ResumeNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "Resume version not found", ex.getMessage());
    }

    @ExceptionHandler(ResumeValidationException.class)
    public ProblemDetail badData(ResumeValidationException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid structured resume (§4.2)", ex.getMessage());
    }

    @ExceptionHandler(ResumeNotReadyException.class)
    public ProblemDetail notReady(ResumeNotReadyException ex) {
        return problem(HttpStatus.CONFLICT, "Resume not ready", ex.getMessage());
    }

    @ExceptionHandler(ResumeStorageException.class)
    public ProblemDetail storage(ResumeStorageException ex) {
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Storage failure", ex.getMessage());
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return problem;
    }
}