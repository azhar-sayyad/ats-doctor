package com.atsdoctor.backend.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AiRunRepository extends JpaRepository<AiRun, UUID> {

    List<AiRun> findByTaskOrderByCreatedAtDesc(String task);
}
