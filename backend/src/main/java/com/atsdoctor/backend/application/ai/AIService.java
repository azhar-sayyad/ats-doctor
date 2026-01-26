package com.atsdoctor.backend.application.ai;

/**
 * The ONLY seam business logic may use for AI calls (PRD §6.1, DEC-002/003).
 * Application code never calls a vendor client directly.
 */
public interface AIService {

    AiResult generate(AiRequest request);
}
