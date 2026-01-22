package com.atsdoctor.backend.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ResumeVersionRepository extends JpaRepository<ResumeVersion, UUID> {

    Optional<ResumeVersion> findTopByResumeIdOrderByVersionDesc(UUID resumeId);
}