package com.atsdoctor.backend;

import com.atsdoctor.backend.api.resume.ResumeDto;
import com.atsdoctor.backend.infrastructure.tailoring.SummaryRestructurer;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** TASK-064 — SummaryRestructurer: evidence-preserving, stable, reversible reordering. */
class SummaryRestructurerTest {

    private final SummaryRestructurer restructurer = new SummaryRestructurer();

    @Test
    void reorders_most_relevant_sections_first() {
        Map<String, Integer> sectionCitations = Map.of("exp_003", 5);
        Map<String, Integer> bulletCitations = Map.of("b1", 3, "b3", 1);

        SummaryRestructurer.Reordered reordered = restructurer.reorder(
                experience(
                        entry("exp_001", "b1", "b2"),
                        entry("exp_002", "b3", "b4"),
                        entry("exp_003", "b5")),
                sectionCitations, bulletCitations);

        assertThat(reordered.orderedIds())
                .containsExactly("exp_003", "exp_001", "exp_002");
    }

    @Test
    void keeps_original_order_on_ties() {
        Map<String, Integer> bulletCitations = Map.of("b1", 1, "b4", 1);

        SummaryRestructurer.Reordered reordered = restructurer.reorder(
                experience(
                        entry("exp_001", "b1", "b2"),
                        entry("exp_002", "b3", "b4")),
                Map.of(), bulletCitations);

        assertThat(reordered.orderedIds()).containsExactly("exp_001", "exp_002");
    }

    @Test
    void never_fabricates_an_order_without_citations() {
        SummaryRestructurer.Reordered reordered = restructurer.reorder(
                experience(
                        entry("exp_001", "b1"),
                        entry("exp_002", "b2")),
                Map.of(), Map.of());

        assertThat(reordered.orderedIds()).containsExactly("exp_001", "exp_002");
    }

    @Test
    void handles_null_and_empty_sections() {
        assertThat(restructurer.reorder(null, Map.of(), Map.of()).orderedIds()).isEmpty();
        assertThat(restructurer.reorder(experience(), Map.of(), Map.of()).orderedIds()).isEmpty();
    }

    private static List<ResumeDto.Experience> experience(ResumeDto.Experience... entries) {
        return List.of(entries);
    }

    private static ResumeDto.Experience entry(String id, String... bulletIds) {
        List<ResumeDto.Bullet> bullets = java.util.Arrays.stream(bulletIds)
                .map(bid -> new ResumeDto.Bullet(bid, "text", List.of(), List.of(), List.of(), "explicit"))
                .toList();
        return new ResumeDto.Experience(id, "Company", "Title", "2020", "present", "Remote", null, bullets);
    }
}