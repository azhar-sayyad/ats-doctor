package com.atsdoctor.backend.infrastructure.tailoring;

import com.atsdoctor.backend.api.resume.ResumeDto;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Resume restructuring — most-relevant experience first (FEAT-031, TASK-064).
 * Relevance = match citations per bullet (matched requirements + JD keyword
 * hits) plus citations on the section's own role evidence row; ties keep the
 * original order, so the reordering is evidence-preserving and reversible (the
 * tailored content stores the reordered sequence while original bullets are
 * never mutated).
 */
@Component
public class SummaryRestructurer {

    public record Reordered(List<String> orderedIds) {
    }

    /**
     * @param sectionCitations section id (e.g. {@code exp_001}) → citation count
     * @param bulletCitations  bullet evidence id (e.g. {@code exp_001_bullet_001}) → citation count
     */
    public Reordered reorder(List<ResumeDto.Experience> experience,
                             Map<String, Integer> sectionCitations,
                             Map<String, Integer> bulletCitations) {
        List<ResumeDto.Experience> sorted = new ArrayList<>();
        if (experience != null) {
            sorted.addAll(experience);
        }
        sorted.sort(Comparator.comparingInt((ResumeDto.Experience e) -> relevance(e, sectionCitations, bulletCitations))
                .reversed());
        return new Reordered(sorted.stream()
                .map(e -> e.id() == null ? "" : e.id())
                .toList());
    }

    private static int relevance(ResumeDto.Experience section,
                                 Map<String, Integer> sectionCitations,
                                 Map<String, Integer> bulletCitations) {
        int score = sectionCitations.getOrDefault(section.id(), 0);
        if (section.bullets() != null) {
            for (ResumeDto.Bullet bullet : section.bullets()) {
                if (bullet != null && bullet.id() != null) {
                    score += bulletCitations.getOrDefault(bullet.id(), 0);
                }
            }
        }
        return score;
    }
}