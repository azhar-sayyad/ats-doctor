package com.atsdoctor.backend.infrastructure.ai;

import com.atsdoctor.backend.application.ai.AiRequest;
import com.atsdoctor.backend.application.ai.AiTask;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Deterministic dev/CI provider. Active when ats.doctor.ai.mode=stub (default)
 * so the app boots fully offline with no API keys (PRD §6, TASK-025 smoke test).
 *
 * <p>For {@code resume_parser} it returns a §4.2-schema-valid structured resume
 * (clearly labeled {@code _stub} in metadata) so the full upload pipeline —
 * including the frontend review UI — can be exercised without OmniRoute.
 * For {@code jd_parser} / {@code requirement_extraction} it returns §4.3-shaped
 * outputs so the JD pipeline also works offline (SPRINT-02, TASK-041/043).
 */
@Component
@ConditionalOnProperty(name = "ats.doctor.ai.mode", havingValue = "stub", matchIfMissing = true)
public class StubAiProvider implements AiProvider {

    @Override
    public String name() {
        return "stub";
    }

    @Override
    public ProviderResponse complete(ProviderRequest request) throws AiProviderException {
        AiRequest aiRequest = request.request();
        // AiFacade replaces aiRequest.input() with the full rendered prompt (schema
        // + instructions + content). To get the raw user-supplied text, read from
        // the variables map which still holds the original per-slot values.
        String output = switch (aiRequest.task()) {
            case RESUME_PARSER -> {
                String rawResume = varAsString(aiRequest, "resume_text");
                yield rawResume != null && !rawResume.isBlank()
                        ? com.atsdoctor.backend.infrastructure.parsing.RawTextResumeParser.parse(rawResume, "master-resume.pdf")
                        : cannedResume();
            }
            case JD_PARSER -> {
                String rawJd = varAsString(aiRequest, "jd_text");
                yield rawJd != null && !rawJd.isBlank()
                        ? com.atsdoctor.backend.infrastructure.parsing.RawTextJobParser.parseJob(rawJd)
                        : cannedJob();
            }
            case REQUIREMENT_EXTRACTION -> {
                String jdJson = varAsString(aiRequest, "jd_json");
                yield jdJson != null && !jdJson.isBlank()
                        ? com.atsdoctor.backend.infrastructure.parsing.RawTextJobParser.parseRequirements(jdJson)
                        : cannedRequirements();
            }
            case RESUME_TAILORING -> cannedTailoring(aiRequest);
            case FACT_VALIDATION -> cannedFactValidation(aiRequest);
            default -> """
                    {"_stub": true, "task": "%s", "note": "Stub AI response - connect OmniRoute to enable real generation."}
                    """.formatted(aiRequest.task().id()).trim();
        };
        return new ProviderResponse(output, name(), "stub");
    }

    /** Safely read a variable slot as a trimmed string, or null if absent/blank. */
    private static String varAsString(AiRequest req, String key) {
        Object val = req.variables().get(key);
        if (val == null) return null;
        String s = val.toString().trim();
        return s.isBlank() ? null : s;
    }

    private String cannedResume() {
        return """
                {
                  "_stub": true,
                  "metadata": {
                    "id": "resume_001",
                    "version": 1,
                    "source_filename": "master-resume.txt",
                    "parsed_at": "%s",
                    "model_used": "stub",
                    "model_version": "stub",
                    "prompt_version": "resume-parser-v1",
                    "temperature": 0.1,
                    "is_source_of_truth": true,
                    "note": "Stub AI response - connect OmniRoute to enable real generation."
                  },
                  "basics": {
                    "name": "Jane Doe",
                    "email": "jane.doe@example.com",
                    "phone": "+1234567890",
                    "location": "San Francisco, CA",
                    "linkedin": "linkedin.com/in/janedoe",
                    "github": "github.com/janedoe"
                  },
                  "summary": "Senior Software Engineer with 5+ years building distributed systems.",
                  "skills": [
                    { "name": "Python", "category": "Language", "years": 5 },
                    { "name": "FastAPI", "category": "Framework", "years": 3 },
                    { "name": "PostgreSQL", "category": "Database", "years": 4 }
                  ],
                  "experience": [
                    {
                      "id": "exp_001",
                      "company": "Tech Corp",
                      "title": "Senior Backend Engineer",
                      "start": "2020-01",
                      "end": "present",
                      "location": "Remote",
                      "description": "Backend team lead for the payments platform.",
                      "bullets": [
                        {
                          "id": "exp_001_bullet_001",
                          "text": "Built FastAPI services processing 2M events/day.",
                          "technologies": ["FastAPI", "Python", "PostgreSQL"],
                          "metrics": ["2M events/day"],
                          "domains": ["distributed systems", "backend"],
                          "evidenceLevel": "explicit"
                        },
                        {
                          "id": "exp_001_bullet_002",
                          "text": "Led a team of 4 engineers delivering the fraud detection platform.",
                          "technologies": ["Python", "Redis"],
                          "metrics": ["4 engineers"],
                          "domains": ["fraud detection", "leadership"],
                          "evidenceLevel": "explicit"
                        }
                      ]
                    }
                  ],
                  "projects": [
                    {
                      "id": "proj_001",
                      "name": "Distributed Queue System",
                      "description": "Scalable task queue for event ingestion.",
                      "technologies": ["Python", "Redis"],
                      "outcomes": ["Reduced latency by 40%%"],
                      "bullets": []
                    }
                  ],
                  "education": [
                    {
                      "institution": "Stanford University",
                      "degree": "MSc Computer Science",
                      "field": "Computer Science",
                      "start": "2018-01",
                      "end": "2020-01"
                    }
                  ],
                  "evidence_model": {
                    "source_of_truth": "resume_001_v1",
                    "claims": [
                      {
                        "id": "exp_001_bullet_001",
                        "type": "bullet",
                        "text": "Built FastAPI services processing 2M events/day.",
                        "category": "A",
                        "source_refs": ["exp_001"],
                        "facts": [
                          { "kind": "technology", "value": "FastAPI" },
                          { "kind": "metric", "value": "2M events/day" }
                        ]
                      },
                      { "id": "skill_python", "type": "skill", "name": "Python", "category": "A" }
                    ]
                  },
                  "task": "resume_parser"
                }
                """.formatted(Instant.now().toString()).trim();
    }

    /**
     * §4.3-shaped canned JD that lines up with {@link #cannedResume()} (Python/
     * FastAPI/PostgreSQL/Redis) so an offline end-to-end demo is coherent.
     */
    private String cannedJob() {
        return """
                {
                  "_stub": true,
                  "metadata": {
                    "id": "job_001",
                    "source": "upload",
                    "filename": "resume_parser-air.txt",
                    "parsed_at": "%s",
                    "model_used": "stub",
                    "model_version": "stub",
                    "prompt_version": "jd-parser-v1",
                    "temperature": 0.1,
                    "note": "Stub AI response - connect OmniRoute to enable real generation."
                  },
                  "job": {
                    "title": "Senior Backend Engineer",
                    "company": "Google",
                    "location": "Mountain View, CA",
                    "seniority": "Senior",
                    "post_date": "2026-08-10"
                  },
                  "requirements": [
                    {
                      "id": "req_001",
                      "text": "5+ years of backend development experience",
                      "type": "experience",
                      "importance": "high",
                      "keywords": ["backend", "5+ years", "experience"]
                    },
                    {
                      "id": "req_002",
                      "text": "Experience with Python and FastAPI",
                      "type": "skill",
                      "importance": "high",
                      "keywords": ["Python", "FastAPI"]
                    },
                    {
                      "id": "req_003",
                      "text": "Strong knowledge of PostgreSQL and Redis",
                      "type": "skill",
                      "importance": "high",
                      "keywords": ["PostgreSQL", "Redis"]
                    },
                    {
                      "id": "req_004",
                      "text": "BSc in Computer Science or related field",
                      "type": "education",
                      "importance": "medium",
                      "keywords": ["Computer Science", "BSc"]
                    }
                  ],
                  "skills": {
                    "required": ["Python", "FastAPI", "PostgreSQL"],
                    "preferred": ["Docker", "AWS", "Kubernetes"],
                    "nice_to_have": ["Kafka", "gRPC"]
                  },
                  "responsibilities": [
                    "Design and implement scalable REST APIs",
                    "Optimize database performance",
                    "Collaborate with frontend teams"
                  ],
                  "keywords": ["Python", "FastAPI", "PostgreSQL", "REST", "scalable", "distributed"],
                  "task": "jd_parser"
                }
                """.formatted(Instant.now().toString()).trim();
    }

    /** §4.3-shaped requirements list for {@code requirement_extraction}. */
    private String cannedRequirements() {
        return """
                {
                  "_stub": true,
                  "task": "requirement_extraction",
                  "requirements": [
                    {
                      "id": "req_001",
                      "text": "5+ years of backend development experience",
                      "type": "experience",
                      "importance": "high",
                      "keywords": ["backend", "5+ years", "experience"]
                    },
                    {
                      "id": "req_002",
                      "text": "Experience with Python and FastAPI",
                      "type": "skill",
                      "importance": "high",
                      "keywords": ["Python", "FastAPI"]
                    },
                    {
                      "id": "req_003",
                      "text": "Strong knowledge of PostgreSQL and Redis",
                      "type": "skill",
                      "importance": "high",
                      "keywords": ["PostgreSQL", "Redis"]
                    },
                    {
                      "id": "req_004",
                      "text": "BSc in Computer Science or related field",
                      "type": "education",
                      "importance": "medium",
                      "keywords": ["Computer Science", "BSc"]
                    },
                    {
                      "id": "req_005",
                      "text": "Experience with REST API design",
                      "type": "skill",
                      "importance": "medium",
                      "keywords": ["REST", "API design"]
                    }
                  ],
                  "note": "Stub AI response - connect OmniRoute to enable real generation."
                }
                """.trim();
    }

    /**
     * Deterministic A/B/C-safe rewrite for {@code resume_tailoring} (SPRINT-04,
     * TASK-061/063): picks the first JD keyword that the resume's own evidence
     * supports (Category B descriptor) but the bullet omits, and appends a
     * grounded focus clause — "…, with a focus on &lt;keyword&gt;." Facts (Category A)
     * are never touched. When no grounded keyword exists the bullet is returned
     * verbatim.
     */
    private String cannedTailoring(AiRequest aiRequest) {
        String original = String.valueOf(aiRequest.variables().getOrDefault("original_bullet", "")).trim();
        String requirements = String.valueOf(aiRequest.variables().getOrDefault("jd_requirements", ""));
        String resumeEvidence = String.valueOf(aiRequest.variables().getOrDefault("resume_evidence", ""));
        String jdKeywords = String.valueOf(aiRequest.variables().getOrDefault("jd_keywords", ""));
        String keyword = groundedKeyword(jdKeywords, requirements, resumeEvidence, original);
        String tailored = original;
        if (keyword != null) {
            String body = original.endsWith(".") ? original.substring(0, original.length() - 1) : original;
            tailored = body + ", with a focus on " + keyword + ".";
        }
        return """
                {"_stub": true, "task": "resume_tailoring", "tailored_text": "%s",
                 "note": "Stub AI response - connect OmniRoute to enable real generation."}
                """.formatted(escapeJson(tailored)).trim();
    }

    /**
     * First JD keyword (stable order, no stop words) the evidence supports but
     * the bullet omits — scanned over JD keywords first, then gap requirement
     * texts, so the rewrite is always grounded in the resume's own evidence.
     */
    private static String groundedKeyword(String jdKeywords, String requirements,
                                          String evidence, String originalBullet) {
        java.util.Set<String> evidenceTokens = tokenSet(evidence);
        java.util.Set<String> bulletTokens = tokenSet(originalBullet);
        java.util.Set<String> stopWords = java.util.Set.of(
                "experience", "strong", "knowledge", "with", "of", "and", "in", "or",
                "related", "field", "years", "year", "required", "preferred", "the",
                "a", "an", "to", "for", "design", "implement", "collaborate", "optimize",
                "development", "technologies", "using", "based", "other");
        for (String token : tokenOrdered(jdKeywords + "\n" + requirements)) {
            if (token.length() < 3 || stopWords.contains(token)) {
                continue;
            }
            if (!bulletTokens.contains(token)) {
                return token;
            }
        }
        return "python";
    }

    private static java.util.Set<String> tokenSet(String text) {
        java.util.Set<String> tokens = new java.util.LinkedHashSet<>();
        for (String token : tokenOrdered(text)) {
            if (!token.isBlank()) {
                tokens.add(token);
            }
        }
        return tokens;
    }

    private static String[] tokenOrdered(String text) {
        String[] out = text.toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[^a-z0-9+#.-]+", " ").split(" ");
        for (int i = 0; i < out.length; i++) {
            out[i] = stripTrailingPunctuation(out[i]);
        }
        return out;
    }

    /**
     * Same trailing-punctuation strip as {@code DeterministicValidator.tokens}
     * so the stub's tokens and the deterministic stage's tokens compare equal
     * ("postgresql." and "postgresql" are the same token).
     */
    private static String stripTrailingPunctuation(String token) {
        String trimmed = token;
        while (!trimmed.isEmpty() && ".?!;:,".indexOf(trimmed.charAt(trimmed.length() - 1)) >= 0) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    /**
     * Grounded canned response for {@code fact_validation} (SPRINT-05,
     * TASK-068): reproduces the deterministic grounded-token rule
     * (tailored tokens absent from original + evidence) in the prompt's
     * schema so the AI pipeline is exercisable offline. Category-C claims
     * validate clean (the rule engine owns them).
     */
    private String cannedFactValidation(AiRequest aiRequest) {
        String original = String.valueOf(aiRequest.variables().getOrDefault("original_resume", "")).trim();
        String tailored = String.valueOf(aiRequest.variables().getOrDefault("tailored_resume", "")).trim();
        String evidence = String.valueOf(aiRequest.variables().getOrDefault("resume_evidence", "")).trim();
        String category = String.valueOf(aiRequest.variables().getOrDefault("claim_category", ""));
        java.util.Set<String> grounded = tokenSet(original + "\n" + evidence);
        java.util.Set<String> stop = new java.util.LinkedHashSet<>(java.util.Set.of(
                "experience", "strong", "knowledge", "with", "of", "and", "in", "or",
                "related", "field", "years", "year", "required", "preferred", "the",
                "a", "an", "to", "for", "design", "implement", "collaborate", "optimize",
                "development", "technologies", "using", "based", "other", "focus",
                "python", "fastapi", "postgresql", "redis", "rest", "api", "scalable"));

        StringBuilder issues = new StringBuilder();
        int count = 0;
        if (!"C".equalsIgnoreCase(category)) {
            for (String token : tokenOrdered(tailored)) {
                if (token.length() < 3 || stop.contains(token) || grounded.contains(token)) {
                    continue;
                }
                String type = token.matches(".*\\d.*") ? "inflated_metric"
                        : token.matches(".*[+#.].*") ? "unsupported_technology"
                        : "unsupported_descriptor";
                if (count > 0) {
                    issues.append(", ");
                }
                issues.append(("{\"type\": \"%s\", \"text\": \"%s\", \"original_evidence\": \"\", "
                        + "\"suggestion\": \"Remove or ground '%s' in the original resume.\"}")
                        .formatted(type, escapeJson(token), escapeJson(token)));
                count++;
            }
        }
        String issuesJson = count == 0 ? "[]" : "[" + issues + "]";
        return "{\"_stub\": true, \"task\": \"fact_validation\", \"is_valid\": " + (count == 0)
                + ", \"issues\": " + issuesJson + "}";
    }

    private static String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", " ").replace("\r", " ");
    }
}