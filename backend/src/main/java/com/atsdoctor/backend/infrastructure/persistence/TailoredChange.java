package com.atsdoctor.backend.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * tailored_changes row (PRD §7.8, FEAT-029, TASK-062): one per rewritten
 * bullet/summary with original/tailored text, reason, claim category and prompt
 * version. {@code evidence_id} is nullable — summary rewrites have no single
 * bullet evidence row. Status starts PENDING; the review lifecycle
 * (ACCEPTED/REJECTED/EDITED/REGENERATED) ships with FEAT-037 (SPRINT-06).
 */
@Entity
@Table(name = "tailored_changes")
public class TailoredChange {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tailored_resume_id", nullable = false)
    private TailoredResume tailoredResume;

    /** Null-safe: absent for summary rewrites. */
    @Column(name = "evidence_id")
    private UUID evidenceId;

    @Column(name = "original_text", nullable = false)
    private String originalText;

    @Column(name = "tailored_text", nullable = false)
    private String tailoredText;

    private String reason;

    /** claim_category: A | B | C */
    @Column(name = "claim_category", nullable = false)
    private String claimCategory;

    /** PENDING | ACCEPTED | REJECTED | EDITED | REGENERATED */
    @Column(nullable = false)
    private String status = "PENDING";

    @Column(name = "prompt_version")
    private String promptVersion;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public TailoredChange() {
    }

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public UUID getId() {
        return id;
    }

    public TailoredResume getTailoredResume() {
        return tailoredResume;
    }

    public void setTailoredResume(TailoredResume tailoredResume) {
        this.tailoredResume = tailoredResume;
    }

    public UUID getEvidenceId() {
        return evidenceId;
    }

    public void setEvidenceId(UUID evidenceId) {
        this.evidenceId = evidenceId;
    }

    public String getOriginalText() {
        return originalText;
    }

    public void setOriginalText(String originalText) {
        this.originalText = originalText;
    }

    public String getTailoredText() {
        return tailoredText;
    }

    public void setTailoredText(String tailoredText) {
        this.tailoredText = tailoredText;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getClaimCategory() {
        return claimCategory;
    }

    public void setClaimCategory(String claimCategory) {
        this.claimCategory = claimCategory;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getPromptVersion() {
        return promptVersion;
    }

    public void setPromptVersion(String promptVersion) {
        this.promptVersion = promptVersion;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}