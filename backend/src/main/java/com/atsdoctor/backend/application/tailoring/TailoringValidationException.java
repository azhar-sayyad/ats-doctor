package com.atsdoctor.backend.application.tailoring;

/** 400 — bad request (missing structured data, unknown analysis id, …). */
public class TailoringValidationException extends RuntimeException {

    public TailoringValidationException(String message) {
        super(message);
    }
}