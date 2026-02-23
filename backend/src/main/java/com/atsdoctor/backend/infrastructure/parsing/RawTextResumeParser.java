package com.atsdoctor.backend.infrastructure.parsing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses raw text extracted from uploaded resume files (PDF/DOCX/TXT) into
 * §4.2 master.json schema JSON strings.
 */
public class RawTextResumeParser {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final Pattern EMAIL_PATTERN = Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}");
    private static final Pattern PHONE_PATTERN = Pattern.compile("(\\+?\\d{1,3}[-.\\s]?)?\\(?\\d{3}\\)?[-.\\s]?\\d{3}[-.\\s]?\\d{4}");
    private static final Pattern LINKEDIN_PATTERN = Pattern.compile("linkedin\\.com/in/[a-zA-Z0-9_-]+", Pattern.CASE_INSENSITIVE);
    private static final Pattern GITHUB_PATTERN = Pattern.compile("github\\.com/[a-zA-Z0-9_-]+", Pattern.CASE_INSENSITIVE);
    private static final Pattern DateRangePattern = Pattern.compile(
            "(\\d{4}(?:[-/.\\s]\\d{1,2})?)\\s*(?:to|[-–—])\\s*(present|\\d{4}(?:[-/.\\s]\\d{1,2})?)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern EducationYearPattern = Pattern.compile("(?:19|20)\\d{2}(?:\\s*[-–—]\\s*(?:19|20)\\d{2})?");
    private static final Pattern DegreePattern = Pattern.compile("(?i)\\b(b\\.?e\\.?|b\\.?tech|b\\.?sc\\.?|m\\.?e\\.?|m\\.?tech|m\\.?sc\\.?|m\\.?ba|bachelor|master|ph\\.?d)");

    public static String parse(String rawText, String sourceFilename) {
        if (rawText == null || rawText.isBlank()) {
            return fallbackStub(sourceFilename);
        }

        String[] lines = rawText.split("\\r?\\n");
        String name = extractName(lines);
        String email = extractRegex(rawText, EMAIL_PATTERN, "");
        String phone = extractRegex(rawText, PHONE_PATTERN, "");
        String linkedin = extractRegex(rawText, LINKEDIN_PATTERN, "");
        String github = extractRegex(rawText, GITHUB_PATTERN, "");
        String location = extractLocation(rawText);

        ObjectNode root = MAPPER.createObjectNode();
        root.put("_stub", false);
        root.put("task", "resume_parser");

        ObjectNode metadata = root.putObject("metadata");
        metadata.put("id", "resume_parsed_" + System.currentTimeMillis());
        metadata.put("version", 1);
        metadata.put("source_filename", sourceFilename != null ? sourceFilename : "master-resume.pdf");
        metadata.put("parsed_at", Instant.now().toString());
        metadata.put("model_used", "text-parser-v1");
        metadata.put("model_version", "v1.0");
        metadata.put("prompt_version", "resume-parser-v1");
        metadata.put("temperature", 0.1);
        metadata.put("is_source_of_truth", true);

        ObjectNode basics = root.putObject("basics");
        basics.put("name", name);
        basics.put("email", email);
        basics.put("phone", phone);
        basics.put("location", location);
        basics.put("linkedin", linkedin);
        basics.put("github", github);

        String summary = extractSummary(rawText);
        root.put("summary", summary);

        ArrayNode skillsNode = root.putArray("skills");
        extractSkills(rawText, skillsNode);

        ArrayNode expNode = root.putArray("experience");
        ArrayNode claimsNode = MAPPER.createArrayNode();
        extractExperience(rawText, expNode, claimsNode);

        ArrayNode projNode = root.putArray("projects");
        extractProjects(rawText, projNode);

        ArrayNode eduNode = root.putArray("education");
        extractEducation(rawText, eduNode);

        ObjectNode evidenceModel = root.putObject("evidence_model");
        evidenceModel.put("source_of_truth", "master_resume_v1");
        evidenceModel.set("claims", claimsNode);

        return root.toString();
    }

    private static String extractName(String[] lines) {
        for (String line : lines) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty() && !trimmed.toLowerCase().contains("resume") && !trimmed.toLowerCase().contains("curriculum")
                    && !EMAIL_PATTERN.matcher(trimmed).find() && trimmed.length() < 60) {
                return trimmed;
            }
        }
        return "Candidate";
    }

    private static String extractRegex(String text, Pattern pattern, String fallback) {
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            return matcher.group();
        }
        return fallback;
    }

    private static String extractLocation(String text) {
        String[] parts = text.split("\\r?\\n");
        for (int i = 0; i < Math.min(5, parts.length); i++) {
            String line = parts[i];
            if (line.contains("|")) {
                for (String chunk : line.split("\\|")) {
                    String c = chunk.trim();
                    if (isLocationChunk(c)) {
                        return c;
                    }
                }
            }
        }
        return "";
    }

    private static boolean isLocationChunk(String chunk) {
        String lower = chunk.toLowerCase();
        if (chunk.contains("@") || lower.contains("linkedin") || lower.contains("github")) {
            return false;
        }
        return containsWord(lower, "remote") || containsWord(lower, "india")
                || containsWord(lower, "california") || containsWord(lower, "new york")
                || containsWord(lower, "texas") || containsWord(lower, "usa")
                || containsWord(lower, "ca") || containsWord(lower, "ny") || containsWord(lower, "tx")
                || lower.contains(",");
    }

    private static String extractSummary(String text) {
        String lower = text.toLowerCase();
        int start = lower.indexOf("summary");
        if (start != -1) {
            int end = lower.indexOf("skills", start);
            if (end == -1) end = lower.indexOf("experience", start);
            if (end != -1 && end > start) {
                String section = text.substring(start, end).replaceFirst("(?i)summary", "").trim();
                return section.replaceAll("\\r?\\n", " ").replaceAll("\\s+", " ").trim();
            }
        }
        return "";
    }

    private static void extractSkills(String text, ArrayNode skillsNode) {
        String lower = text.toLowerCase();
        int start = lower.indexOf("skills");
        if (start != -1) {
            int end = lower.indexOf("experience", start);
            if (end == -1) end = lower.indexOf("projects", start);
            String block = end != -1 ? text.substring(start, end) : text.substring(start);
            for (String line : block.split("\\r?\\n")) {
                line = line.trim();
                if (line.contains(":")) {
                    String[] parts = line.split(":", 2);
                    String category = parts[0].trim();
                    for (String skill : parts[1].split("[,|]")) {
                        String name = skill.trim();
                        if (!name.isEmpty()) {
                            ObjectNode s = skillsNode.addObject();
                            s.put("name", name);
                            s.put("category", category);
                            s.put("years", 3);
                        }
                    }
                }
            }
        }
    }

    private static void extractExperience(String text, ArrayNode expNode, ArrayNode claimsNode) {
        String[] allLines = text.split("\\r?\\n");
        int sectionStart = -1;
        for (int i = 0; i < allLines.length; i++) {
            String lower = allLines[i].trim().toLowerCase();
            if (lower.equals("experience") || lower.startsWith("work experience")
                    || lower.startsWith("professional experience") || lower.startsWith("technical experience")
                    || lower.startsWith("employment history") || lower.startsWith("relevant experience")) {
                sectionStart = i;
                break;
            }
        }
        if (sectionStart == -1) return;

        StringBuilder blockBuilder = new StringBuilder();
        for (int i = sectionStart; i < allLines.length; i++) {
            String trimmed = allLines[i].trim();
            String lower = trimmed.toLowerCase();
            if (i > sectionStart && (isSectionHeader(trimmed) && !lower.startsWith("experience"))) {
                break;
            }
            blockBuilder.append(trimmed).append('\n');
        }
        String[] lines = blockBuilder.toString().split("\\r?\\n");
        ObjectNode currentExp = null;
        ArrayNode currentBullets = null;
        int expIndex = 1;

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.equalsIgnoreCase("experience")) continue;

            boolean isBullet = trimmed.startsWith("•") || trimmed.startsWith("-") || trimmed.startsWith("*") || isActionVerb(trimmed);

            if (isBullet) {
                if (currentExp == null) continue;
                String bulletText = (trimmed.startsWith("•") || trimmed.startsWith("-") || trimmed.startsWith("*"))
                        ? trimmed.substring(1).trim()
                        : trimmed;
                String bulletId = currentExp.get("id").asText() + "_bullet_" + (currentBullets.size() + 1);
                ObjectNode b = currentBullets.addObject();
                b.put("id", bulletId);
                b.put("text", bulletText);
                b.putArray("technologies");
                b.putArray("metrics");
                b.putArray("domains");
                b.put("evidenceLevel", "explicit");

                ObjectNode claim = claimsNode.addObject();
                claim.put("id", bulletId);
                claim.put("type", "bullet");
                claim.put("text", bulletText);
                claim.put("category", "A");
                ArrayNode refs = claim.putArray("source_refs");
                refs.add(currentExp.get("id").asText());
                claim.putArray("facts");
            } else if (line.length() < 100 && !isSectionHeader(trimmed)) {
                currentExp = expNode.addObject();
                currentExp.put("id", "exp_" + expIndex);
                String comp = trimmed;
                String tit = "";
                if (trimmed.contains("—") || trimmed.contains("–") || trimmed.contains(" - ")) {
                    String[] parts = trimmed.split("[-—–]", 2);
                    comp = parts[0].trim();
                    tit = parts[1].trim();
                }
                Matcher years = DateRangePattern.matcher(trimmed);
                if (years.find()) {
                    currentExp.put("start", years.group(1));
                    currentExp.put("end", years.group(2));
                }
                if (!tit.isEmpty()) {
                    Matcher titleYears = DateRangePattern.matcher(tit);
                    if (titleYears.find()) {
                        tit = (tit.substring(0, titleYears.start()) + " " + tit.substring(titleYears.end()))
                                .replaceAll("[()\\s]+", " ").trim();
                    }
                    currentExp.put("title", tit);
                }
                currentExp.put("company", comp);
                currentExp.put("location", "");
                currentExp.put("description", trimmed);
                currentBullets = currentExp.putArray("bullets");
                expIndex++;
            }
        }
    }

    private static boolean isSectionHeader(String line) {
        String lower = line.toLowerCase();
        return lower.equals("skills") || lower.equals("projects") || lower.equals("education")
                || lower.equals("summary") || lower.equals("certifications") || lower.equals("awards")
                || lower.equals("languages") || lower.equals("interests") || lower.equals("contact")
                || lower.startsWith("work experience") || lower.startsWith("professional experience")
                || lower.startsWith("technical skills");
    }

    private static boolean isActionVerb(String line) {
        String lower = line.toLowerCase();
        String[] verbs = {
                "built", "developed", "led", "engineered", "designed", "created",
                "implemented", "reduced", "increased", "managed", "integrated",
                "architected", "pioneered", "spearheaded", "orchestrated", "automated",
                "constructed", "delivered", "formulated", "maintained", "improved",
                "expanded", "optimized", "scale", "scaled"
        };
        for (String verb : verbs) {
            if (lower.startsWith(verb + " ")) {
                return true;
            }
        }
        return false;
    }

    private static void extractProjects(String text, ArrayNode projNode) {
        String lower = text.toLowerCase();
        int start = lower.indexOf("projects");
        if (start == -1) return;
        int end = lower.indexOf("education", start);
        String block = end != -1 ? text.substring(start, end) : text.substring(start);

        for (String line : block.split("\\r?\\n")) {
            String trimmed = line.trim();
            if (trimmed.length() > 5 && !trimmed.equalsIgnoreCase("projects") && !trimmed.startsWith("•")) {
                ObjectNode p = projNode.addObject();
                p.put("id", "proj_" + (projNode.size() + 1));
                p.put("name", trimmed);
                p.put("description", trimmed);
                p.putArray("technologies");
                p.putArray("outcomes");
                p.putArray("bullets");
                if (projNode.size() >= 3) break;
            }
        }
    }

    private static void extractEducation(String text, ArrayNode eduNode) {
        String lower = text.toLowerCase();
        int start = lower.indexOf("education");
        if (start == -1) return;
        String block = text.substring(start);

        for (String line : block.split("\\r?\\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.equalsIgnoreCase("education") || trimmed.length() < 5) continue;
            if (isSectionHeader(trimmed)) break;
            ObjectNode e = eduNode.addObject();
            String institution = trimmed;
            Matcher ym = EducationYearPattern.matcher(trimmed);
            if (ym.find()) {
                String range = ym.group();
                String[] ys = range.split("\\s*[-–—]\\s*");
                e.put("start", ys[0]);
                if (ys.length > 1) e.put("end", ys[1]);
                institution = (trimmed.substring(0, ym.start()) + " " + trimmed.substring(ym.end()))
                        .replaceAll("\\s+", " ").trim();
            }
            Matcher dm = DegreePattern.matcher(institution);
            if (dm.find()) {
                e.put("degree", dm.group().replaceAll("\\s+", " ").trim());
            }
            String[] commaParts = institution.split(",");
            String inst = commaParts[commaParts.length - 1].trim();
            for (int i = commaParts.length - 1; i >= 0 && inst.isEmpty(); i--) {
                inst = commaParts[i].trim();
            }
            e.put("institution", inst);
            break;
        }
    }

    private static boolean containsWord(String lowerText, String word) {
        return Pattern.compile("(?i)\\b" + Pattern.quote(word) + "\\b").matcher(lowerText).find();
    }

    private static String fallbackStub(String filename) {
        return """
                {
                  "_stub": true,
                  "metadata": { "id": "fallback_001", "source_filename": "%s" },
                  "basics": { "name": "Candidate Name", "email": "candidate@example.com", "phone": "", "location": "Remote" },
                  "summary": "Software Engineer",
                  "skills": [], "experience": [], "projects": [], "education": []
                }
                """.formatted(filename != null ? filename : "resume.pdf");
    }
}
