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
 * job_requirements row (PRD §7.5, FEAT-020, TASK-044): typed requirements
 * extracted from a JD (skill/experience/education, high/medium/low).
 */
@Entity
@Table(name = "job_requirements")
public class JobRequirement {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_id", nullable = false)
    private Job job;

    @Column(nullable = false)
    private String text;

    /** type: skill | experience | education */
    @Column(nullable = false)
    private String type;

    /** importance: high | medium | low */
    @Column(nullable = false)
    private String importance;

    @JdbcTypeCode(SqlTypes.ARRAY)
    private String[] keywords = new String[0];

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public JobRequirement() {
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

    public Job getJob() {
        return job;
    }

    public void setJob(Job job) {
        this.job = job;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getImportance() {
        return importance;
    }

    public void setImportance(String importance) {
        this.importance = importance;
    }

    public String[] getKeywords() {
        return keywords == null ? new String[0] : keywords;
    }

    public void setKeywords(String[] keywords) {
        this.keywords = keywords == null ? new String[0] : keywords;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}