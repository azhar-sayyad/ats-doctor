package com.atsdoctor.backend.api.jobs;

import com.atsdoctor.backend.application.job.JobNotFoundException;
import com.atsdoctor.backend.application.job.JobStorageException;
import com.atsdoctor.backend.application.job.JobValidationException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * RFC 7807 ProblemDetail mapping for job endpoints (07-api-contract, PRD §8).
 */
@RestControllerAdvice(assignableTypes = JobController.class)
@ConditionalOnProperty(name = "ats.doctor.persistence.enabled", havingValue = "true")
public class JobExceptionHandler {

    @ExceptionHandler(JobNotFoundException.class)
    public ProblemDetail notFound(JobNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "Job not found", ex.getMessage());
    }

    @ExceptionHandler(JobValidationException.class)
    public ProblemDetail badData(JobValidationException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid structured JD (§4.3)", ex.getMessage());
    }

    @ExceptionHandler(JobStorageException.class)
    public ProblemDetail storage(JobStorageException ex) {
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Storage failure", ex.getMessage());
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return problem;
    }
}