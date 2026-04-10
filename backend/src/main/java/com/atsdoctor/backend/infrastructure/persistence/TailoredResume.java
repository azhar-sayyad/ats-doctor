package com.atsdoctor.backend.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * tailored_resumes row (PRD §7.7, FEAT-032, TASK-065). {@code state} follows the
 * Tailoring state machine (§5.8): QUEUED → GENERATING → VALIDATING → READY →
 * NEEDS_REVIEW → APPROVED. Tailoring never chains — this row references the
 * analysis (and, denormalized, its exact master resume version).
 * {@code score_before} is the analysis score; {@code score_after} is a
 * deterministic MatchPipeline re-run over the tailored content. The tailored
 * document lives in {@code content} JSONB; per-bullet rewrites are
 * {@link TailoredChange} rows. {@code validation} holds the rule-engine
 * report from the VALIDATING stage / revalidation (FEAT-035, TASK-071).
 */
@Entity
@Table(name = "tailored_resumes")
public class TailoredResume {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "analysis_id", nullable = false)
    private Analysis analysis;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "resume_version_id", nullable = false)
    private ResumeVersion resumeVersion;

    /** TailoringState name: QUEUED → GENERATING → VALIDATING → READY / … */
    @Column(nullable = false)
    private String state;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String content;

    /** Rule-engine validation report JSON (FEAT-035, TASK-071); null until first validation. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String validation;

    /** Full editable tailored document JSON; null until the user saves an edit via PUT /tailored/{id}/edit. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String document;

    /** Export template slug (resume-templates.yml, CL-020); defaults to ats_clean. */
    @Column(nullable = false, length = 64)
    private String template = "ats_clean";

    /** Rendered ATS-friendly HTML (SPRINT-06 export, TASK-079); null until first export. */
    @Column(columnDefinition = "TEXT")
    private String html;

    /** Absolute path of the rendered PDF artifact (TASK-080); null until first export. */
    @Column(name = "pdf_path", length = 512)
    private String pdfPath;

    /** Absolute path of the rendered DOCX artifact (TASK-081); null until first export. */
    @Column(name = "docx_path", length = 512)
    private String docxPath;

    @Column(name = "score_before")
    private Integer scoreBefore;

    @Column(name = "score_after")
    private Integer scoreAfter;

    private String error;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public TailoredResume() {
    }

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public Analysis getAnalysis() {
        return analysis;
    }

    public void setAnalysis(Analysis analysis) {
        this.analysis = analysis;
    }

    public ResumeVersion getResumeVersion() {
        return resumeVersion;
    }

    public void setResumeVersion(ResumeVersion resumeVersion) {
        this.resumeVersion = resumeVersion;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getValidation() {
        return validation;
    }

    public void setValidation(String validation) {
        this.validation = validation;
    }

    public String getDocument() {
        return document;
    }

    public void setDocument(String document) {
        this.document = document;
    }

    public String getTemplate() {
        return template;
    }

    public void setTemplate(String template) {
        this.template = template;
    }

    public String getHtml() {
        return html;
    }

    public void setHtml(String html) {
        this.html = html;
    }

    public String getPdfPath() {
        return pdfPath;
    }

    public void setPdfPath(String pdfPath) {
        this.pdfPath = pdfPath;
    }

    public String getDocxPath() {
        return docxPath;
    }

    public void setDocxPath(String docxPath) {
        this.docxPath = docxPath;
    }

    public Integer getScoreBefore() {
        return scoreBefore;
    }

    public void setScoreBefore(Integer scoreBefore) {
        this.scoreBefore = scoreBefore;
    }

    public Integer getScoreAfter() {
        return scoreAfter;
    }

    public void setScoreAfter(Integer scoreAfter) {
        this.scoreAfter = scoreAfter;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}