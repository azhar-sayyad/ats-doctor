package com.atsdoctor.backend.infrastructure.parsing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Parses raw text extracted from uploaded or pasted Job Descriptions into
 * §4.3 structured.json schema JSON strings.
 */
public class RawTextJobParser {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static String parseJob(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return fallbackJob();
        }

        String[] lines = rawText.split("\\r?\\n");
        String title = extractTitle(lines);
        String company = extractCompany(lines, rawText);
        String location = extractLocation(rawText);

        ObjectNode root = MAPPER.createObjectNode();
        root.put("_stub", false);
        root.put("task", "jd_parser");

        ObjectNode metadata = root.putObject("metadata");
        metadata.put("id", "job_parsed_" + System.currentTimeMillis());
        metadata.put("source", "pasted_text");
        metadata.put("filename", "job_target.txt");
        metadata.put("parsed_at", Instant.now().toString());
        metadata.put("model_used", "text-parser-v1");
        metadata.put("model_version", "v1.0");
        metadata.put("prompt_version", "jd-parser-v1");
        metadata.put("temperature", 0.1);

        ObjectNode job = root.putObject("job");
        job.put("title", title);
        job.put("company", company);
        job.put("location", location);
        job.put("seniority", seniorityOf(title));
        job.put("post_date", "2026-01-01");
        job.put("url", "");

        ArrayNode reqsNode = root.putArray("requirements");
        List<String> keywords = new ArrayList<>();
        extractRequirementsAndKeywords(rawText, reqsNode, keywords);

        ObjectNode skillsNode = root.putObject("skills");
        ArrayNode reqSkills = skillsNode.putArray("required");
        for (String k : keywords) {
            reqSkills.add(k);
        }
        skillsNode.putArray("preferred");
        skillsNode.putArray("niceToHave");

        ArrayNode respNode = root.putArray("responsibilities");
        extractResponsibilities(rawText, respNode);

        ArrayNode kwNode = root.putArray("keywords");
        for (String k : keywords) {
            kwNode.add(k);
        }

        return root.toString();
    }

    public static String parseRequirements(String rawText) {
        ArrayNode reqs = MAPPER.createArrayNode();
        if (rawText != null) {
            // The stub receives the full structured JD JSON (from JobDto.toJson) — try
            // to extract the requirements array from it directly before falling back to
            // raw-text heuristics.
            boolean parsedFromJson = tryParseFromJdJson(rawText, reqs);
            if (!parsedFromJson) {
                String[] lines = rawText.split("\\r?\\n");
                int reqIndex = 1;
                for (String line : lines) {
                    String trimmed = line.trim();
                    if ((trimmed.startsWith("\u2022") || trimmed.startsWith("-") || trimmed.startsWith("*") || trimmed.toLowerCase().contains("experience") || trimmed.toLowerCase().contains("knowledge")) && trimmed.length() > 10) {
                        String clean = trimmed.startsWith("\u2022") || trimmed.startsWith("-") || trimmed.startsWith("*") ? trimmed.substring(1).trim() : trimmed;
                        ObjectNode r = reqs.addObject();
                        r.put("id", "req_" + reqIndex);
                        r.put("text", clean);
                        r.put("type", classifyRequirementType(clean));
                        r.put("importance", classifyRequirementImportance(clean));
                        ArrayNode kws = r.putArray("keywords");
                        for (String kw : extractTechKeywords(clean)) {
                            kws.add(kw);
                        }
                        reqIndex++;
                        if (reqIndex > 10) break;
                    }
                }
            }
        }
        return reqs.toString();
    }

    /**
     * Attempt to read requirements from the structured JD JSON (§4.3 shape).
     * Returns true if the JSON had a non-empty {@code requirements} array.
     */
    private static boolean tryParseFromJdJson(String text, ArrayNode out) {
        try {
            com.fasterxml.jackson.databind.JsonNode root = MAPPER.readTree(text);
            com.fasterxml.jackson.databind.JsonNode reqsNode = root.path("requirements");
            if (!reqsNode.isArray() || reqsNode.isEmpty()) {
                return false;
            }
            int index = 1;
            for (com.fasterxml.jackson.databind.JsonNode node : reqsNode) {
                String reqText = node.path("text").asText("").trim();
                if (reqText.isBlank()) continue;
                String type = node.path("type").asText("hard_skill").trim();
                String importance = node.path("importance").asText("should_have").trim();
                // Normalize type/importance to DB CHECK-constraint values
                if (!java.util.Set.of("skill", "experience", "education").contains(type)) {
                    type = reqText.toLowerCase().contains("degree") ? "education" : "skill";
                }
                if (!java.util.Set.of("high", "medium", "low", "must_have", "should_have", "nice_to_have", "required", "preferred").contains(importance)) {
                    importance = "medium";
                }
                ObjectNode r = out.addObject();
                r.put("id", "req_" + index);
                r.put("text", reqText);
                r.put("type", type);
                r.put("importance", importance);
                // Read existing keywords from JSON or derive from tech stack
                com.fasterxml.jackson.databind.JsonNode existingKws = node.path("keywords");
                ArrayNode kws = r.putArray("keywords");
                if (existingKws.isArray() && !existingKws.isEmpty()) {
                    existingKws.forEach(k -> { if (!k.asText("").isBlank()) kws.add(k.asText().toLowerCase()); });
                } else {
                    for (String kw : extractTechKeywords(reqText)) kws.add(kw);
                }
                index++;
                if (index > 10) break;
            }
            return out.size() > 0;
        } catch (Exception ignored) {
            return false;
        }
    }

    /** Extract recognized tech stack tokens from a requirement sentence. */
    private static List<String> extractTechKeywords(String text) {
        String lower = text.toLowerCase();
        String[] techList = {
                "java", "spring boot", "python", "django", "fastapi", "react", "next.js",
                "postgresql", "kafka", "aws", "docker", "kubernetes", "redis",
                "rest apis", "microservices", "golang", "typescript", "graphql"
        };
        List<String> found = new ArrayList<>();
        for (String tech : techList) {
            if (lower.contains(tech)) {
                found.add(tech);
            }
        }
        return found;
    }

    private static String extractTitle(String[] lines) {
        String[] roleWords = {
                "engineer", "developer", "lead", "architect", "manager",
                "analyst", "sre", "support", "administrator", "specialist", "consultant"
        };
        for (String line : lines) {
            String trimmed = line.trim();
            String lower = trimmed.toLowerCase();
            boolean isBullet = trimmed.startsWith("\u2022") || trimmed.startsWith("-") || trimmed.startsWith("*")
                    || trimmed.matches("\\d+[.)].*");
            boolean isSectionHeader = containsAny(lower, "role requirements", "responsibilities",
                    "qualifications", "requirements", "overview", "about the role",
                    "job description", "summary", "key responsibilities");
            if (!isBullet && !isSectionHeader && trimmed.length() > 4 && trimmed.length() < 70
                    && containsAny(lower, roleWords)) {
                if (trimmed.contains("(")) {
                    trimmed = trimmed.substring(0, trimmed.indexOf('(')).trim();
                }
                return trimmed;
            }
        }
        return "Untitled Role";
    }

    private static String seniorityOf(String title) {
        String lower = title.toLowerCase();
        if (lower.contains("senior") || lower.contains("sr ") || lower.contains("sr.")) {
            return "senior";
        }
        if (lower.contains("junior") || lower.contains("jr ") || lower.contains("jr.")) {
            return "junior";
        }
        if (lower.contains("lead")) {
            return "lead";
        }
        if (lower.contains("principal")) {
            return "principal";
        }
        return "";
    }

    private static String extractCompany(String[] lines, String rawText) {
        String lower = rawText.toLowerCase();
        String[][] knownCompanies = {
                {"google", "Google"},
                {"microsoft", "Microsoft"},
                {"amazon", "Amazon"},
                {"meta", "Meta"},
                {"facebook", "Meta"},
                {"optum", "Optum"},
                {"unitedhealth", "UnitedHealth Group"},
                {"united health", "UnitedHealth Group"},
                {"tech corp", "Tech Corp"},
                {"infonover", "Infonover Technologies"}
        };
        for (String[] pair : knownCompanies) {
            if (containsWord(lower, pair[0])) {
                return pair[1];
            }
        }
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.length() > 2 && trimmed.length() <= 120
                    && (trimmed.toLowerCase().contains("inc") || trimmed.toLowerCase().contains("corp")
                    || trimmed.toLowerCase().contains("technologies") || trimmed.toLowerCase().contains("solutions"))) {
                return trimmed;
            }
        }
        return "";
    }

    private static String extractLocation(String text) {
        String lower = text.toLowerCase();
        if (containsWord(lower, "remote")) return "Remote";
        if (containsWord(lower, "india")) return "India";
        if (containsWord(lower, "california") || containsWord(lower, "ca")) return "California, USA";
        if (containsWord(lower, "new york") || containsWord(lower, "ny")) return "New York, USA";
        if (containsWord(lower, "texas") || containsWord(lower, "tx")) return "Texas, USA";
        if (containsWord(lower, "united states") || containsWord(lower, "usa")) return "United States";
        return "";
    }

    private static boolean containsWord(String lowerText, String word) {
        return Pattern.compile("(?i)\\b" + Pattern.quote(word) + "\\b").matcher(lowerText).find();
    }

    private static boolean containsAny(String lowerText, String... tokens) {
        for (String token : tokens) {
            if (lowerText.contains(token)) {
                return true;
            }
        }
        return false;
    }

    private static String classifyRequirementType(String text) {
        String lower = text.toLowerCase();
        if (containsAny(lower, "degree", "bsc", "msc", "bachelor", "master", "bs in", "b.tech", "m.tech", "mba")) {
            return "education";
        }
        if (containsAny(lower, "experience", "years", "year of", "background", "track record")) {
            return "experience";
        }
        return "skill";
    }

    private static String classifyRequirementImportance(String text) {
        String lower = text.toLowerCase();
        if (containsAny(lower, "must", "required", "essential", "mandatory", "minimum")) {
            return "high";
        }
        if (containsAny(lower, "preferred", "nice to have", "plus", "bonus")) {
            return "medium";
        }
        if (containsAny(lower, "years", "experience", "degree", "proficien", "strong", "expert")) {
            return "medium";
        }
        return "low";
    }

    private static void extractRequirementsAndKeywords(String text, ArrayNode reqsNode, List<String> keywords) {
        String[] lines = text.split("\\r?\\n");
        int count = 1;
        for (String line : lines) {
            String trimmed = line.trim();
            boolean isBullet = trimmed.startsWith("•") || trimmed.startsWith("-") || trimmed.startsWith("*");
            boolean isProse = !isBullet
                    && trimmed.length() > 15 && trimmed.length() < 200
                    && (trimmed.toLowerCase().contains("experience")
                    || trimmed.toLowerCase().contains("knowledge")
                    || trimmed.toLowerCase().contains("required")
                    || trimmed.toLowerCase().contains("preferred")
                    || trimmed.toLowerCase().contains("degree")
                    || trimmed.toLowerCase().contains("proficien"));
            if ((isBullet || isProse) && trimmed.length() > 10) {
                String reqText = isBullet ? trimmed.substring(1).trim() : trimmed;
                ObjectNode r = reqsNode.addObject();
                r.put("id", "req_" + count);
                r.put("text", reqText);
                r.put("type", classifyRequirementType(reqText));
                r.put("importance", classifyRequirementImportance(reqText));
                ArrayNode kws = r.putArray("keywords");
                for (String kw : extractTechKeywords(reqText)) {
                    kws.add(kw);
                }
                count++;
                if (count > 10) break;
            }
        }

        // Extract Tech Keywords for the whole JD
        String lower = text.toLowerCase();
        String[] techList = {"java", "spring boot", "python", "django", "fastapi", "react", "next.js", "postgresql", "kafka", "aws", "docker", "kubernetes", "redis", "rest apis", "microservices"};
        for (String tech : techList) {
            if (lower.contains(tech)) {
                keywords.add(tech);
            }
        }
    }

    private static void extractResponsibilities(String text, ArrayNode respNode) {
        String[] lines = text.split("\\r?\\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("•") || trimmed.startsWith("-")) {
                respNode.add(trimmed.substring(1).trim());
                if (respNode.size() >= 5) break;
            }
        }
    }

    private static String fallbackJob() {
        return """
                {
                  "_stub": true,
                  "job": { "title": "Senior Backend Engineer", "company": "Tech Corp", "location": "Remote", "seniority": "senior" },
                  "requirements": [], "skills": { "required": [], "preferred": [], "niceToHave": [] },
                  "responsibilities": [], "keywords": []
                }
                """;
    }
}
