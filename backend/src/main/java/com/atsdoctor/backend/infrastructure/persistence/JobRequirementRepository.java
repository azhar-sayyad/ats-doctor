package com.atsdoctor.backend.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface JobRequirementRepository extends JpaRepository<JobRequirement, UUID> {

    List<JobRequirement> findByJobId(UUID jobId);

    long countByJobId(UUID jobId);
}