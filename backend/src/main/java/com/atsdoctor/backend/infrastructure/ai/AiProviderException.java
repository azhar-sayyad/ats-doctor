package com.atsdoctor.backend.infrastructure.ai;

/**
 * Raised by an AiProvider when a completion fails (network, timeout, HTTP error,
 * malformed response). The facade retries and/or falls back on this exception;
 * other exceptions are programming errors and propagate immediately.
 */
public class AiProviderException extends RuntimeException {

    public AiProviderException(String message) {
        super(message);
    }

    public AiProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}
