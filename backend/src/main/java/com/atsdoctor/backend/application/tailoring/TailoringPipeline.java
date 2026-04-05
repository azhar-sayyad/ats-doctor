package com.atsdoctor.backend.application.tailoring;

import com.atsdoctor.backend.api.jobs.JobDto;
import com.atsdoctor.backend.api.resume.ResumeDto;
import com.atsdoctor.backend.domain.states.TailoringState;
import com.atsdoctor.backend.infrastructure.matching.EvidenceDoc;
import com.atsdoctor.backend.infrastructure.matching.MatchPipeline;
import com.atsdoctor.backend.infrastructure.matching.MatchRequest;
import com.atsdoctor.backend.infrastructure.matching.MatchingResult;
import com.atsdoctor.backend.infrastructure.matching.RequirementData;
import com.atsdoctor.backend.infrastructure.persistence.Analysis;
import com.atsdoctor.backend.infrastructure.persistence.JobRequirement;
import com.atsdoctor.backend.infrastructure.persistence.JobRequirementRepository;
import com.atsdoctor.backend.infrastructure.persistence.ResumeEvidence;
import com.atsdoctor.backend.infrastructure.persistence.ResumeEvidenceRepository;
import com.atsdoctor.backend.infrastructure.persistence.TailoredResume;
import com.atsdoctor.backend.infrastructure.persistence.TailoredResumeRepository;
import com.atsdoctor.backend.infrastructure.scoring.ScoreCalculator;
import com.atsdoctor.backend.infrastructure.tailoring.BulletKeys;
import com.atsdoctor.backend.infrastructure.tailoring.BulletRewriter;
import com.atsdoctor.backend.infrastructure.tailoring.SummaryRestructurer;
import com.atsdoctor.backend.infrastructure.tailoring.TailorDecider;
import com.atsdoctor.backend.infrastructure.tailoring.TailoringContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Async tailoring pipeline (FEAT-028..032, TASK-059..066):
 * QUEUED → GENERATING (TailorDecider + per-bullet/summary AI rewrites via
 * {@code resume_tailoring}, recorded in {@code ai_runs}) → VALIDATING (rule
 * engine over the changes, persisted {@code validation} report — TASK-071) →
 * READY with content JSON, tailored_changes rows and a deterministic
 * {@code score_after} (MatchPipeline re-run over the tailored content).
 *
 * <p>Tailoring never chains: the row references the analysis and its exact
 * master resume version. Failures set {@code error} on the row (TailoringState
 * has no FAILED per §5.8 — TASK-089).
 */
@Component
@ConditionalOnProperty(name = "ats.doctor.persistence.enabled", havingValue = "true")
public class TailoringPipeline {

    private static final Logger log = LoggerFactory.getLogger(TailoringPipeline.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final TailoringService tailoringService;
    private final TailoredResumeRepository tailoredResumeRepository;
    private final JobRequirementRepository jobRequirementRepository;
    private final ResumeEvidenceRepository resumeEvidenceRepository;
    private final TailorDecider tailorDecider;
    private final BulletRewriter bulletRewriter;
    private final SummaryRestructurer summaryRestructurer;
    private final MatchPipeline matchPipeline;
    private final ScoreCalculator scoreCalculator;
    private final Validator validator;
    private final com.atsdoctor.backend.application.validation.ValidationService validationService;
    private final org.springframework.transaction.support.TransactionTemplate transactionTemplate;

    public TailoringPipeline(TailoringService tailoringService,
                             TailoredResumeRepository tailoredResumeRepository,
                             JobRequirementRepository jobRequirementRepository,
                             ResumeEvidenceRepository resumeEvidenceRepository,
                             TailorDecider tailorDecider,
                             BulletRewriter bulletRewriter,
                             SummaryRestructurer summaryRestructurer,
                             MatchPipeline matchPipeline,
                             ScoreCalculator scoreCalculator,
                             Validator validator,
                             com.atsdoctor.backend.application.validation.ValidationService validationService,
                             org.springframework.transaction.PlatformTransactionManager transactionManager) {
        this.tailoringService = tailoringService;
        this.tailoredResumeRepository = tailoredResumeRepository;
        this.jobRequirementRepository = jobRequirementRepository;
        this.resumeEvidenceRepository = resumeEvidenceRepository;
        this.tailorDecider = tailorDecider;
        this.bulletRewriter = bulletRewriter;
        this.summaryRestructurer = summaryRestructurer;
        this.matchPipeline = matchPipeline;
        this.scoreCalculator = scoreCalculator;
        this.validator = validator;
        this.validationService = validationService;
        this.transactionTemplate = new org.springframework.transaction.support.TransactionTemplate(transactionManager);
    }

    @Async("resumeTaskExecutor")
    @EventListener
    public void onQueued(TailoringQueuedEvent event) {
        UUID tailoredId = event.tailoredResumeId();
        try {
            tailoringService.mark(tailoredId, TailoringState.GENERATING);
            transactionTemplate.executeWithoutResult(status -> runPipeline(tailoredId));
        } catch (Exception ex) {
            tailoringService.completeFailure(tailoredId, ex);
            log.error("Tailoring {} failed", tailoredId, ex);
        }
    }

    private void runPipeline(UUID tailoredId) {
        TailoredResume tailored = tailoredResumeRepository.findById(tailoredId)
                .orElseThrow(() -> new TailoringNotFoundException("No tailored resume found for id " + tailoredId));
            Analysis analysis = tailored.getAnalysis();
            JobDto jobDto = JobDto.parse(analysis.getJob().getStructuredData(), validator);
            ResumeDto resumeDto = ResumeDto.parse(tailored.getResumeVersion().getStructuredData(), validator);

            List<TailorDecider.BulletSelection> selections = new ArrayList<>();
            List<ResumeEvidence> evidenceRows =
                    resumeEvidenceRepository.findByResumeVersionId(tailored.getResumeVersion().getId());
            TailoringContext context = buildContext(jobDto, resumeDto, analysis, evidenceRows);
            TailorDecider.Decision decision = tailorDecider.decide(context);
            selections.addAll(decision.bulletSelections());

            // --- rewrite phase (FEAT-029/030; one ai_runs row per call) ---
            Map<String, String> tailoredBySectionId = new LinkedHashMap<>();
            List<TailoringService.TailoringChangeData> changes = new ArrayList<>();
            for (TailorDecider.BulletSelection selection : selections) {
                BulletRewriter.RewrittenText rewritten = bulletRewriter.rewriteBullet(
                        selection.originalText(), context.gaps().stream().map(TailoringContext.RequirementGap::text).toList(),
                        context.allEvidenceText(),
                        context.jdKeywords());
                if (!rewritten.text().equals(selection.originalText())) {
                    changes.add(new TailoringService.TailoringChangeData(
                            evidenceIdFor(selection.evidenceId(), evidenceRows),
                            selection.originalText(),
                            rewritten.text(),
                            reasonFor(selection.requirementText()),
                            selection.claimCategory(),
                            rewritten.promptVersion()));
                    tailoredBySectionId.put(selection.bulletId(), rewritten.text());
                }
            }

            SummaryRewrite summaryRewrite = null;
            if (decision.rewriteSummary() && resumeDto.summary() != null && !resumeDto.summary().isBlank()) {
                BulletRewriter.RewrittenText rewritten = bulletRewriter.rewriteSummary(
                        resumeDto.summary(),
                        context.gaps().stream().map(TailoringContext.RequirementGap::text).toList(),
                        context.allEvidenceText(),
                        context.jdKeywords());
                if (!rewritten.text().equals(resumeDto.summary())) {
                    summaryRewrite = new SummaryRewrite(
                            resumeDto.summary(), rewritten.text(), rewritten.promptVersion());
                    changes.add(new TailoringService.TailoringChangeData(
                            null, resumeDto.summary(), rewritten.text(),
                            "Summary alignment with JD keywords.",
                            "B", rewritten.promptVersion()));
                }
            }

            tailoringService.mark(tailoredId, TailoringState.VALIDATING);

            // --- restructuring (FEAT-031, TASK-064): most-relevant first ---
            Map<String, Integer> sectionCitations = new LinkedHashMap<>();
            Map<String, Integer> bulletCitations = new LinkedHashMap<>();
            collectSectionCitations(resumeDto, analysis, tailored.getResumeVersion().getId(),
                    sectionCitations, bulletCitations);
            List<String> order = summaryRestructurer
                    .reorder(resumeDto.experience(), sectionCitations, bulletCitations).orderedIds();

            // --- score_after: deterministic MatchPipeline re-run over tailored content ---
            int scoreAfter = recomputeScore(tailored, tailoredBySectionId,
                    summaryRewrite == null ? resumeDto.summary() : summaryRewrite.tailoredText());

            String contentJson = write(contentJson(resumeDto, tailoredBySectionId, summaryRewrite, order));
            tailoringService.completeSuccess(tailoredId, contentJson, scoreAfter, changes);
            // VALIDATING runs for real (PRD §5.8, TASK-071): the rule engine
            // checks every change and persists the validation report on the row.
            validationService.validate(tailoredId);
            log.info("Tailored resume {} complete: score {} → {}, {} changes",
                    tailoredId, tailored.getScoreBefore(), scoreAfter, changes.size());
    }

    // ------------------------------------------------------------------

    private TailoringContext buildContext(JobDto jobDto, ResumeDto resumeDto,
                                          Analysis analysis,
                                          List<ResumeEvidence> evidenceRows) {
        List<JobRequirement> rows = new ArrayList<>(jobRequirementRepository.findByJobId(analysis.getJob().getId()));
        rows.sort(Comparator
                .comparing((JobRequirement r) -> r.getCreatedAt() == null ? java.time.Instant.MIN : r.getCreatedAt())
                .thenComparing(r -> r.getId() == null ? UUID.randomUUID() : r.getId()));
        Map<String, List<String>> keywordsByText = new LinkedHashMap<>();
        rows.forEach(r -> keywordsByText.put(r.getText(),
                r.getKeywords() == null ? List.of() : Arrays.stream(r.getKeywords())
                        .flatMap(k -> Arrays.stream(k.split(",")))
                        .map(String::trim).filter(s -> !s.isBlank()).toList()));

        List<TailoringContext.RequirementGap> gaps = new ArrayList<>();
        for (JsonNode match : matchesOf(analysis)) {
            String text = match.path("requirement_text").asText("");
            if (!"matched".equals(match.path("status").asText("")) && !text.isBlank()) {
                gaps.add(new TailoringContext.RequirementGap(
                        text, keywordsByText.getOrDefault(text, List.of())));
            }
        }
        // When all requirements are already matched (fully-scored resume), there
        // are no gaps — but we still want to align bullets with JD keywords for
        // maximum ATS relevance. Fall back to using all requirements as alignment
        // targets so TailorDecider can still select and rewrite bullets.
        if (gaps.isEmpty()) {
            for (Map.Entry<String, List<String>> entry : keywordsByText.entrySet()) {
                gaps.add(new TailoringContext.RequirementGap(entry.getKey(), entry.getValue()));
            }
        }

        StringBuilder allEvidence = new StringBuilder();
        for (ResumeEvidence row : evidenceRows) {
            allEvidence.append('\n').append(row.getText());
        }
        return new TailoringContext(
                resumeDto,
                gaps,
                jobDto.keywords() == null ? List.of() : jobDto.keywords(),
                allEvidence.toString());
    }

    private List<JsonNode> matchesOf(Analysis analysis) {
        if (analysis.getMatches() == null || analysis.getMatches().isBlank()) {
            return List.of();
        }
        try {
            JsonNode node = MAPPER.readTree(analysis.getMatches());
            if (node.isArray()) {
                List<JsonNode> out = new ArrayList<>();
                node.forEach(out::add);
                return out;
            }
        } catch (Exception ignored) {
            // fall through — treat as no gaps
        }
        return List.of();
    }

    /** Match-citation counts per section: bullet citations for bullets, role citations for section ids. */
    private void collectSectionCitations(ResumeDto resumeDto, Analysis analysis, UUID resumeVersionId,
                                         Map<String, Integer> sectionCitations,
                                         Map<String, Integer> bulletCitations) {
        List<String> sectionIds = new ArrayList<>();
        if (resumeDto.experience() != null) {
            for (ResumeDto.Experience exp : resumeDto.experience()) {
                if (exp.id() != null) {
                    sectionIds.add(exp.id());
                }
            }
        }
        Map<String, String> sectionOfEvidenceId = new LinkedHashMap<>();
        for (ResumeEvidence row : resumeEvidenceRepository.findByResumeVersionId(resumeVersionId)) {
            if (row.getId() != null && row.getSectionId() != null) {
                sectionOfEvidenceId.put(row.getId().toString(), row.getSectionId());
            }
        }
        for (JsonNode match : matchesOf(analysis)) {
            JsonNode evidence = match.path("evidence");
            if (!evidence.isArray()) {
                continue;
            }
            for (JsonNode hit : evidence) {
                String sectionId = sectionOfEvidenceId.get(hit.path("id").asText(""));
                if (sectionId == null) {
                    continue;
                }
                (sectionIds.contains(sectionId) ? sectionCitations : bulletCitations)
                        .merge(sectionId, 1, Integer::sum);
            }
        }
    }

    private int recomputeScore(TailoredResume tailored, Map<String, String> tailoredBySectionId,
                               String newSummary) {
        Analysis analysis = tailored.getAnalysis();
        JobDto jobDto = JobDto.parse(analysis.getJob().getStructuredData(), validator);
        ResumeDto resumeDto = ResumeDto.parse(tailored.getResumeVersion().getStructuredData(), validator);

        List<EvidenceDoc> evidence = new ArrayList<>();
        int rowIndex = 0;
        for (ResumeEvidence row : resumeEvidenceRepository.findByResumeVersionId(tailored.getResumeVersion().getId())) {
            String id = row.getId() != null ? row.getId().toString() : "evidence:" + rowIndex;
            String text = tailoredBySectionId.getOrDefault(row.getSectionId(), row.getText());
            evidence.add(new EvidenceDoc(id, text));
            rowIndex++;
        }
        StringBuilder resumeText = new StringBuilder(optional(newSummary));
        if (resumeDto.education() != null) {
            int i = 0;
            for (ResumeDto.Education edu : resumeDto.education()) {
                String label = String.join(" ",
                        optional(edu.institution()), optional(edu.degree()), optional(edu.field())).trim();
                if (!label.isBlank()) {
                    evidence.add(new EvidenceDoc("edu_" + (i++), label));
                    resumeText.append('\n').append(label);
                }
            }
        }
        for (EvidenceDoc doc : evidence) {
            resumeText.append('\n').append(doc.text());
        }

        Integer maxYears = null;
        if (resumeDto.skills() != null) {
            maxYears = resumeDto.skills().stream()
                    .map(ResumeDto.Skill::years)
                    .filter(y -> y != null)
                    .max(Integer::compareTo)
                    .orElse(null);
        }

        List<JobRequirement> rows = new ArrayList<>(jobRequirementRepository.findByJobId(analysis.getJob().getId()));
        rows.sort(Comparator
                .comparing((JobRequirement r) -> r.getCreatedAt() == null ? java.time.Instant.MIN : r.getCreatedAt())
                .thenComparing(r -> r.getId() == null ? UUID.randomUUID() : r.getId()));
        List<RequirementData> requirements = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            var row = rows.get(i);
            requirements.add(new RequirementData(
                    "req_" + (i + 1), row.getText(), row.getType(), row.getImportance(),
                    row.getKeywords() == null ? List.of() : List.of(row.getKeywords())));
        }

        MatchRequest request = new MatchRequest(
                requirements,
                jobDto.keywords() == null ? List.of() : jobDto.keywords(),
                jobDto.responsibilities() == null ? List.of() : jobDto.responsibilities(),
                jobDto.job() == null ? null : jobDto.job().seniority(),
                evidence,
                resumeText.toString(),
                maxYears);
        MatchingResult result = matchPipeline.run(request);
        ScoreCalculator.ScoreOutcome outcome = scoreCalculator.calculate(
                result.requirements().stream().map(r -> new ScoreCalculator.MatchRequirement(
                        r.requirement().text(), r.requirement().type(), r.requirement().importance(), r.status())).toList(),
                result.keywordHits().stream().map(k -> new ScoreCalculator.MatchKeyword(k.keyword(), k.matched())).toList(),
                result.responsibilities().stream().map(r -> new ScoreCalculator.MatchResponsibility(r.text(), r.matched())).toList(),
                new ScoreCalculator.MatchSeniority(result.seniority().jdSeniority(), result.seniority().aligned()));
        return outcome.total();
    }

    private Map<String, Object> contentJson(ResumeDto resumeDto,
                                            Map<String, String> tailoredBySectionId,
                                            SummaryRewrite summaryRewrite,
                                            List<String> order) {
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("summary", summaryRewrite == null ? null : Map.of(
                "original", summaryRewrite.originalText(),
                "tailored", summaryRewrite.tailoredText()));
        List<Map<String, Object>> experience = new ArrayList<>();
        List<ResumeDto.Experience> sections = resumeDto.experience() == null ? List.of() : resumeDto.experience();
        for (ResumeDto.Experience section : sections) {
            List<Map<String, Object>> bullets = new ArrayList<>();
            if (section.bullets() != null) {
                for (ResumeDto.Bullet bullet : section.bullets()) {
                    String bulletId = BulletKeys.bulletId(sections, section, section.bullets(), bullet);
                    String tailored = tailoredBySectionId.get(bulletId);
                    if (tailored != null) {
                        Map<String, Object> b = new LinkedHashMap<>();
                        b.put("original_id", bulletId);
                        b.put("original_text", bullet.text());
                        b.put("tailored_text", tailored);
                        bullets.add(b);
                    }
                }
            }
            if (!bullets.isEmpty()) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("id", section.id() == null ? "" : section.id());
                entry.put("company", optional(section.company()));
                entry.put("title", optional(section.title()));
                entry.put("bullets", bullets);
                experience.add(entry);
            }
        }
        content.put("experience", experience);
        content.put("order", order);
        Map<String, Object> generation = new LinkedHashMap<>();
        generation.put("mode", "ai");
        generation.put("ai_enabled", true);
        generation.put("prompt_version", com.atsdoctor.backend.application.ai.AiTask.RESUME_TAILORING.promptVersion());
        content.put("generation", generation);
        return content;
    }

    /** One summarized rewrite (summary variant of the bullet rewrite). */
    public record SummaryRewrite(String originalText, String tailoredText, String promptVersion) {
    }

    /** §4.4-shaped reason, e.g. "Better alignment with JD keywords ('kubernetes')." */
    private static String reasonFor(String requirementText) {
        List<String> words = new ArrayList<>();
        for (String token : com.atsdoctor.backend.infrastructure.matching.Normalizer.tokens(requirementText)) {
            if (token.length() >= 3 && !STOP_WORDS.contains(token)) {
                words.add(token);
            }
            if (words.size() == 2) {
                break;
            }
        }
        String quoted = words.stream().map(w -> "'" + w + "'").reduce((a, b) -> a + ", " + b).orElse("");
        return "Better alignment with JD keywords (" + quoted + ").";
    }

    private static final java.util.Set<String> STOP_WORDS = java.util.Set.of(
            "experience", "strong", "knowledge", "with", "of", "and", "in", "or",
            "related", "field", "years", "year", "required", "preferred", "the",
            "a", "an", "to", "for", "design", "implement", "collaborate", "optimize");

    private static UUID uuidOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    /** Evidence row id for a section id — bullet evidence rows use {@code section_id = bullet id}. */
    private static UUID evidenceIdFor(String sectionId, List<ResumeEvidence> evidenceRows) {
        if (sectionId != null && !sectionId.isBlank()) {
            for (ResumeEvidence row : evidenceRows) {
                if (row.getId() != null && sectionId.equals(row.getSectionId())) {
                    return row.getId();
                }
            }
        }
        return evidenceRows.isEmpty() ? null : evidenceRows.get(0).getId();
    }

    private static String write(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Could not serialize tailoring output", ex);
        }
    }

    private static String optional(String value) {
        return value == null ? "" : value;
    }
}