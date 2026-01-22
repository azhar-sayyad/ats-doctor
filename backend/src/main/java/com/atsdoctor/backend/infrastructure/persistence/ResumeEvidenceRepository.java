package com.atsdoctor.backend.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ResumeEvidenceRepository extends JpaRepository<ResumeEvidence, UUID> {

    List<ResumeEvidence> findByResumeVersionId(UUID resumeVersionId);

    long countByResumeVersionId(UUID resumeVersionId);
}