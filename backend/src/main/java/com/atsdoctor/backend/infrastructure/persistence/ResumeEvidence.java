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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * resume_evidence row (PRD §7.3, FEAT-014, TASK-033/034): claims split from the
 * structured resume into A (fact) / B (supported descriptor) / C (unsupported).
 */
@Entity
@Table(name = "resume_evidence")
public class ResumeEvidence {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "resume_version_id", nullable = false)
    private ResumeVersion resumeVersion;

    /** section: experience | skills | projects */
    @Column(nullable = false)
    private String section;

    @Column(name = "section_id")
    private String sectionId;

    @Column(nullable = false)
    private String text;

    @Column(name = "normalized_text")
    private String normalizedText;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String metadata;

    /** claim_category: A | B | C */
    @Column(name = "claim_category", nullable = false)
    private String claimCategory;

    /** Evidence IDs this claim traces to — empty in the MVP (FEAT-049). */
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "source_refs")
    private UUID[] sourceRefs = new UUID[0];

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public ResumeEvidence() {
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

    public ResumeVersion getResumeVersion() {
        return resumeVersion;
    }

    public void setResumeVersion(ResumeVersion resumeVersion) {
        this.resumeVersion = resumeVersion;
    }

    public String getSection() {
        return section;
    }

    public void setSection(String section) {
        this.section = section;
    }

    public String getSectionId() {
        return sectionId;
    }

    public void setSectionId(String sectionId) {
        this.sectionId = sectionId;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getNormalizedText() {
        return normalizedText;
    }

    public void setNormalizedText(String normalizedText) {
        this.normalizedText = normalizedText;
    }

    public String getMetadata() {
        return metadata;
    }

    public void setMetadata(String metadata) {
        this.metadata = metadata;
    }

    public String getClaimCategory() {
        return claimCategory;
    }

    public void setClaimCategory(String claimCategory) {
        this.claimCategory = claimCategory;
    }

    public UUID[] getSourceRefs() {
        return sourceRefs == null ? new UUID[0] : sourceRefs;
    }

    public void setSourceRefs(UUID[] sourceRefs) {
        this.sourceRefs = sourceRefs == null ? new UUID[0] : sourceRefs;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}