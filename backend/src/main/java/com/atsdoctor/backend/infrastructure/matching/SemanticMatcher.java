package com.atsdoctor.backend.infrastructure.matching;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Semantic (embedding) matching hook (TASK-051, FEAT-024) — a stub. The MVP is
 * exact-first by design (PRD §5.3); embeddings land with FEAT-049 (pgvector,
 * SPRINT-08+). Kept as a real bean so the pipeline has a single seam to flip
 * later: {@code ats.doctor.matching.semantic-enabled=true} consults it today
 * and it returns no matches (with a warning log).
 */
@Component
public class SemanticMatcher {

    private static final Logger log = LoggerFactory.getLogger(SemanticMatcher.class);

    private final boolean enabled;

    public SemanticMatcher(@Value("${ats.doctor.matching.semantic-enabled:false}") boolean enabled) {
        this.enabled = enabled;
    }

    public boolean enabled() {
        return enabled;
    }

    /** @return empty — semantic matching is not implemented yet (FEAT-049). */
    public Optional<EvidenceHit> findSemanticMatch(String hint) {
        if (enabled) {
            log.warn("Semantic matching requested but not implemented (FEAT-049) — no match for: {}", hint);
        }
        return Optional.empty();
    }
}
