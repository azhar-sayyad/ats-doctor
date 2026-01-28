package com.atsdoctor.backend.application.job;

import java.util.UUID;

/**
 * Published after a JD creation transaction commits; the async pipeline
 * listener picks it up (CREATED → EXTRACTING → PARSING → READY/FAILED).
 */
public record JobCreatedEvent(UUID jobId) {
}