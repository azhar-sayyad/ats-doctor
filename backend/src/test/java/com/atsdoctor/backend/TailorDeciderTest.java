package com.atsdoctor.backend;

import com.atsdoctor.backend.api.resume.ResumeDto;
import com.atsdoctor.backend.infrastructure.tailoring.TailorDecider;
import com.atsdoctor.backend.infrastructure.tailoring.TailoringContext;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** TASK-060 — TailorDecider: selects gap bullets, skips matched, no-op safe. */
class TailorDeciderTest {

    private final TailorDecider decider = new TailorDecider();

    @Test
    void selects_bullets_that_do_not_cover_gap_keywords() {
        TailoringContext context = context(
                bullet("b1", "Built FastAPI services processing 2M events/day.", "A"),
                bullet("b2", "Led a team of 4 engineers delivering the fraud detection platform.", "B"),
                summary("Senior Software Engineer with 5+ years building distributed systems."),
                gap("Experience with REST API design", List.of("REST", "API design")));

        TailorDecider.Decision decision = decider.decide(context);

        assertThat(decision.bulletSelections()).hasSize(2);
        assertThat(decision.bulletSelections()).allSatisfy(s -> {
            assertThat(s.originalText()).isNotBlank();
            assertThat(s.evidenceId()).isEqualTo(s.bulletId());
        });
        assertThat(decision.rewriteSummary()).isTrue();
    }

    @Test
    void skips_bullets_that_already_cover_the_gap() {
        TailoringContext context = context(
                bullet("b1", "Built FastAPI services processing 2M events/day.", "A"),
                bullet("b2", "Designed REST APIs with Python.", "A"),
                gap("Experience with REST API design", List.of("REST", "API design")));

        TailorDecider.Decision decision = decider.decide(context);

        assertThat(decision.bulletSelections())
                .extracting(TailorDecider.BulletSelection::bulletId)
                .containsExactly("b1");
    }

    @Test
    void no_op_when_everything_is_covered_or_no_gaps() {
        TailoringContext covered = context(
                bullet("b1", "REST API design experience.", "A"),
                gap("Experience with REST API design", List.of("REST", "API design")));
        assertThat(decider.decide(covered).isEmpty()).isTrue();

        TailoringContext noGaps = context(
                bullet("b1", "Built FastAPI services.", "A"),
                summary("Senior Engineer."));
        assertThat(decider.decide(noGaps).isEmpty()).isTrue();
    }

    @Test
    void caps_rewrites_at_max_budget() {
        TailoringContext context = context(
                bullet("b1", "One.", "A"),
                bullet("b2", "Two.", "A"),
                bullet("b3", "Three.", "A"),
                bullet("b4", "Four.", "A"),
                gap("Kubernetes experience", List.of("Kubernetes")),
                gap("gRPC experience", List.of("gRPC")));

        TailorDecider.Decision decision = decider.decide(context);

        assertThat(decision.bulletSelections()).hasSize(TailorDecider.MAX_BULLET_REWRITES);
    }

    @Test
    void no_summary_rewrite_when_resume_has_no_summary() {
        TailoringContext context = context(
                bullet("b1", "Built FastAPI services.", "A"),
                gap("Kubernetes experience", List.of("Kubernetes")));
        assertThat(decider.decide(context).rewriteSummary()).isFalse();
    }

    @Test
    void categories_follow_the_bullet_classification() {
        TailoringContext context = context(
                bullet("b1", "Built FastAPI services processing 2M events/day.", "A"),
                bullet("b2", "Helped with team standups.", "C"),
                gap("Kubernetes experience", List.of("Kubernetes")));
        TailorDecider.Decision decision = decider.decide(context);

        assertThat(decision.bulletSelections())
                .extracting(TailorDecider.BulletSelection::claimCategory)
                .containsExactlyInAnyOrder("A", "B");
    }

    @Test
    void id_less_bullets_get_stable_synthetic_keys() {
        TailoringContext context = context(
                bullet(null, "Built FastAPI services processing 2M events/day.", "A"),
                bullet(null, "Led a team of 4 engineers delivering the fraud detection platform.", "B"),
                bullet(null, "Designed automated reporting with cron workflows.", "B"),
                bullet(null, "Built Kafka pipelines for real-time streaming.", "B"),
                gap("Kubernetes experience", List.of("Kubernetes")),
                gap("gRPC experience", List.of("gRPC")));

        TailorDecider.Decision decision = decider.decide(context);

        assertThat(decision.bulletSelections())
                .extracting(TailorDecider.BulletSelection::bulletId)
                .containsExactly("exp_001_bullet_1", "exp_001_bullet_2", "exp_001_bullet_3");
        assertThat(decision.bulletSelections())
                .allSatisfy(s -> assertThat(s.evidenceId()).isEqualTo(s.bulletId()));
    }

    // ------------------------------------------------------------------

    private static TailoringContext context(TailoringContext.Bullet bullet,
                                            TailoringContext.RequirementGap gap) {
        return context(List.of(bullet), null, List.of(gap));
    }

    private static TailoringContext context(TailoringContext.Bullet bullet, String summary) {
        return context(List.of(bullet), summary, List.of());
    }

    private static TailoringContext context(TailoringContext.Bullet b1, TailoringContext.Bullet b2,
                                            TailoringContext.RequirementGap gap) {
        return context(List.of(b1, b2), null, List.of(gap));
    }

    private static TailoringContext context(TailoringContext.Bullet b1, TailoringContext.Bullet b2,
                                            String summary, TailoringContext.RequirementGap gap) {
        return context(List.of(b1, b2), summary, List.of(gap));
    }

    private static TailoringContext context(TailoringContext.Bullet b1, TailoringContext.Bullet b2,
                                            TailoringContext.Bullet b3, TailoringContext.Bullet b4,
                                            TailoringContext.RequirementGap g1, TailoringContext.RequirementGap g2) {
        return context(List.of(b1, b2, b3, b4), null, List.of(g1, g2));
    }

    private static TailoringContext context(TailoringContext.Bullet b1, TailoringContext.Bullet b2,
                                            TailoringContext.Bullet b3, TailoringContext.RequirementGap gap) {
        return context(List.of(b1, b2, b3), null, List.of(gap));
    }

    private static TailoringContext context(List<TailoringContext.Bullet> bullets, String summary,
                                            List<TailoringContext.RequirementGap> gaps) {
        return new TailoringContext(
                resume(summary, bullets),
                gaps,
                List.of(),
                "FastAPI Python REST backend distributed");
    }

    private static ResumeDto resume(String summary, List<TailoringContext.Bullet> bullets) {
        List<ResumeDto.Experience> experience = List.of(new ResumeDto.Experience(
                "exp_001", "Tech Corp", "Senior Backend Engineer",
                "2020-01", "present", "Remote", "Backend team lead.",
                bullets.stream().map(b -> new ResumeDto.Bullet(
                        b.bulletId(), b.originalText(), List.of(), List.of(),
                        List.of(), "A".equals(b.claimCategory()) ? "explicit" : "derived")).toList()));
        return new ResumeDto(
                null, new ResumeDto.Basics("Jane Doe", null, null, null, null, null),
                summary, null, experience, null, null, null);
    }

    private static TailoringContext.Bullet bullet(String id, String text, String category) {
        return new TailoringContext.Bullet(id, text, category, id);
    }

    private static String summary(String text) {
        return text;
    }

    private static TailoringContext.RequirementGap gap(String text, List<String> keywords) {
        return new TailoringContext.RequirementGap(text, keywords);
    }
}