package com.atsdoctor.backend.application.tailoring;

/** 409 — analysis not READY yet, or a tailoring run is already in progress. */
public class TailoringConflictException extends RuntimeException {

    public TailoringConflictException(String message) {
        super(message);
    }
}