package com.atsdoctor.backend.application.job;

/** Storage failure while persisting a JD file (FEAT-017, TASK-039) → 500. */
public class JobStorageException extends RuntimeException {

    public JobStorageException(String message) {
        super(message);
    }
}