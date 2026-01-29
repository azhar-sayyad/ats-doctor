package com.atsdoctor.backend.application.tailoring;

/** 404 — no tailored resume for the id. */
public class TailoringNotFoundException extends RuntimeException {

    public TailoringNotFoundException(String message) {
        super(message);
    }
}