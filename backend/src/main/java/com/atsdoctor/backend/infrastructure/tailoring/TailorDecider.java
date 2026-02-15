package com.atsdoctor.backend.infrastructure.tailoring;

import com.atsdoctor.backend.api.resume.ResumeDto;
import com.atsdoctor.backend.infrastructure.matching.Normalizer;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Selective tailoring decision layer (FEAT-028, TASK-059): decides WHICH
 * bullets to rewrite and whether the summary needs alignment. Deterministic —
 * consumes the analysis gaps (unmatched requirements) and rewrites only bullets
 * that do not already cover the gap keywords; fully matched bullets are
 * skipped. No-op when nothing needs tailoring.
 */
@Component
public class TailorDecider {

    /** Upper bound of bullets rewritten per run — keeps the AI budget bounded. */
    public static final int MAX_BULLET_REWRITES = 3;

    public record Decision(
            List<BulletSelection> bulletSelections,
            boolean rewriteSummary) {

        public boolean isEmpty() {
            return bulletSelections.isEmpty() && !rewriteSummary;
        }
    }

    public record BulletSelection(
            String bulletId,
            String originalText,
            String claimCategory,
            String evidenceId,
            String requirementText) {
    }

    public Decision decide(TailoringContext context) {
        List<BulletSelection> selections = new ArrayList<>();
        boolean rewriteSummary = false;

        List<ResumeDto.Experience> experience = context.resume() == null
                ? List.of()
                : context.resume().experience() == null ? List.of() : context.resume().experience();

        List<TailoringContext.Bullet> bullets = flattenBullets(experience);
        Set<String> selectedIds = new LinkedHashSet<>();

        for (TailoringContext.RequirementGap gap : context.gaps()) {
            for (TailoringContext.Bullet bullet : bullets) {
                if (selections.size() >= MAX_BULLET_REWRITES) {
                    break;
                }
                if (selectedIds.contains(bullet.bulletId())) {
                    continue;
                }
                if (coversGap(gap.keywords(), bullet.originalText())) {
                    continue;
                }
                selectedIds.add(bullet.bulletId());
                selections.add(new BulletSelection(
                        bullet.bulletId(), bullet.originalText(),
                        bullet.claimCategory(), bullet.evidenceId(), gap.text()));
            }
            if (selections.size() >= MAX_BULLET_REWRITES) {
                break;
            }
        }

        if (!rewriteSummary) {
            rewriteSummary = needsSummary(context);
        }

        return new Decision(selections, rewriteSummary);
    }

    private static boolean coversGap(List<String> keywords, String bulletText) {
        if (keywords == null || keywords.isEmpty()) {
            return false;
        }
        Set<String> bulletTokens = Normalizer.tokens(bulletText);
        for (String keyword : keywords) {
            Set<String> tokens = Normalizer.tokens(keyword);
            for (String token : tokens) {
                if (token.length() < 3) continue;
                boolean found = false;
                for (String bToken : bulletTokens) {
                    if (bToken.equalsIgnoreCase(token) || bToken.startsWith(token) || token.startsWith(bToken)) {
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean needsSummary(TailoringContext context) {
        return context.resume() != null
                && context.resume().summary() != null
                && !context.resume().summary().isBlank()
                && !context.gaps().isEmpty();
    }

    private static List<TailoringContext.Bullet> flattenBullets(List<ResumeDto.Experience> experience) {
        List<TailoringContext.Bullet> out = new ArrayList<>();
        for (ResumeDto.Experience exp : experience) {
            if (exp.bullets() == null) {
                continue;
            }
            for (ResumeDto.Bullet bullet : exp.bullets()) {
                if (bullet == null || bullet.text() == null || bullet.text().isBlank()) {
                    continue;
                }
                // Section evidence id = bullet id (EvidenceExtractor maps
                // experience bullets to section_id = bullet id).
                String sectionId = exp.id() == null ? "exp_" + (experience.indexOf(exp) + 1) : exp.id();
                String bulletId = bullet.id() == null
                        ? sectionId + "_bullet_" + (exp.bullets().indexOf(bullet) + 1)
                        : bullet.id();
                out.add(new TailoringContext.Bullet(
                        bulletId, bullet.text(), classify(bullet), bulletId));
            }
        }
        return out;
    }

    /** Mirrors EvidenceExtractor.classifyBullet: explicit facts are A, else B. */
    private static String classify(ResumeDto.Bullet bullet) {
        if ("explicit".equals(bullet.evidenceLevel())
                || (bullet.metrics() != null && !bullet.metrics().isEmpty())
                || (bullet.technologies() != null && !bullet.technologies().isEmpty())) {
            return "A";
        }
        return "B";
    }
}