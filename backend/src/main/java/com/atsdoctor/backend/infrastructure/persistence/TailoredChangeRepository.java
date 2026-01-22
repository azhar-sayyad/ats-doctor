package com.atsdoctor.backend.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TailoredChangeRepository extends JpaRepository<TailoredChange, UUID> {

    List<TailoredChange> findByTailoredResumeIdOrderByCreatedAtAsc(UUID tailoredResumeId);
}