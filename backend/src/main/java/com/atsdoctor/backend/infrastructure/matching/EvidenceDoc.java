package com.atsdoctor.backend.infrastructure.matching;

/** One piece of resume text the matcher can cite (resume_evidence row, education degree, …). */
public record EvidenceDoc(String id, String text, String normalizedText) {

    public EvidenceDoc(String id, String text) {
        this(id, text, Normalizer.normalize(text));
    }
}
