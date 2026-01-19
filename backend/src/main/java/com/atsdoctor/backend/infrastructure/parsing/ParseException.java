package com.atsdoctor.backend.infrastructure.parsing;

/**
 * Raised when a resume file cannot be parsed (empty content, image-only PDF,
 * corrupt/incorrect format). Message is safe to surface to the user.
 */
public class ParseException extends Exception {

    public ParseException(String message) {
        super(message);
    }

    public ParseException(String message, Throwable cause) {
        super(message, cause);
    }
}