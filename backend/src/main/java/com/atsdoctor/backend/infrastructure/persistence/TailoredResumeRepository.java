package com.atsdoctor.backend.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TailoredResumeRepository extends JpaRepository<TailoredResume, UUID> {

    List<TailoredResume> findByAnalysisIdOrderByCreatedAtDesc(UUID analysisId);
}