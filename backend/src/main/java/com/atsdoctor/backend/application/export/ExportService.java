package com.atsdoctor.backend.application.export;

import com.atsdoctor.backend.application.tailoring.TailoringConflictException;
import com.atsdoctor.backend.application.tailoring.TailoringNotFoundException;
import com.atsdoctor.backend.infrastructure.persistence.TailoredChangeRepository;
import com.atsdoctor.backend.infrastructure.persistence.TailoredResume;
import com.atsdoctor.backend.infrastructure.persistence.TailoredResumeRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lowagie.text.DocumentException;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.xhtmlrenderer.pdf.ITextRenderer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Tailored-resume export (FEAT-037, SPRINT-06, TASK-079..081): renders the
 * tailored document (post-review changes) as ATS-friendly HTML
 * ({@code templates/export/resume.html}), PDF via Flying Saucer + OpenPDF and
 * DOCX via Apache POI XWPF. Artifacts are stored under
 * {@code ats.doctor.storage.exports-dir}; {@code html}, {@code pdf_path} and
 * {@code docx_path} track them on the row.
 *
 * <p>Gate (locked with the sprint plan): an export is 409 until {@code state}
 * is past the pipeline (READY / NEEDS_REVIEW / APPROVED), every change is
 * resolved (no PENDING) AND the persisted validation report is
 * {@code valid=true}. JSON export reuses the GET /tailored/{id} shape
 * (controller side — §8.2).
 */
@Service
@ConditionalOnProperty(name = "ats.doctor.persistence.enabled", havingValue = "true")
public class ExportService {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ISO_INSTANT;

    private final TailoredResumeRepository tailoredResumeRepository;
    private final TailoredChangeRepository tailoredChangeRepository;
    private final SpringTemplateEngine templateEngine;
    private final Path exportsRoot;

    public ExportService(TailoredResumeRepository tailoredResumeRepository,
                         TailoredChangeRepository tailoredChangeRepository,
                         SpringTemplateEngine exportTemplateEngine,
                         @Value("${ats.doctor.storage.exports-dir:data/exports}") String exportsDir) {
        this.tailoredResumeRepository = tailoredResumeRepository;
        this.tailoredChangeRepository = tailoredChangeRepository;
        this.templateEngine = exportTemplateEngine;
        this.exportsRoot = Path.of(exportsDir).toAbsolutePath().normalize();
    }

    public record ExportArtifact(String mediaType, String filename, byte[] content) {
    }

    /** One exportable section: heading (company + title) and tailored bullets. */
    public record Section(String heading, List<String> bullets) {
    }

    public record Basics(String name, String email, String phone, String location, String linkedin, String github) {
    }

    public record Skill(String name, String category) {
    }

    public record Project(String name, String description) {
    }

    public record Education(String institution, String degree, String field, String end) {
    }

    /** Rendered-document model shared by the HTML template and the DOCX writer. */
    public record DocModel(String title, Basics basics, String summary, List<Skill> skills,
                           List<Section> sections, List<Project> projects, List<Education> education,
                           Integer scoreBefore, Integer scoreAfter, String generatedAt) {
    }

    /** Export gate — 409 until every change is resolved AND validation passed; 404 unknown. */
    @Transactional(readOnly = true)
    public void assertExportable(UUID tailoredResumeId) {
        TailoredResume tailored = require(tailoredResumeId);
        String state = tailored.getState();
        if (!"READY".equals(state) && !"NEEDS_REVIEW".equals(state) && !"APPROVED".equals(state)) {
            throw new TailoringConflictException(
                    "Tailored resume " + tailoredResumeId + " is not ready for export (state=" + state + ")");
        }
        boolean pending = tailoredChangeRepository.findByTailoredResumeIdOrderByCreatedAtAsc(tailoredResumeId)
                .stream().anyMatch(c -> "PENDING".equals(c.getStatus()));
        if (pending) {
            throw new TailoringConflictException(
                    "Tailored resume " + tailoredResumeId + " has changes still pending review — cannot export");
        }
        if (tailored.getValidation() == null
                || !validationValid(tailored.getValidation())) {
            throw new TailoringConflictException(
                    "Tailored resume " + tailoredResumeId + " has not passed validation — cannot export");
        }
    }

    @Transactional
    public byte[] html(UUID tailoredResumeId) {
        TailoredResume tailored = require(tailoredResumeId);
        assertExportable(tailoredResumeId);
        String html = render(modelOf(tailored));
        tailored.setHtml(html);
        tailoredResumeRepository.save(tailored);
        return html.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    @Transactional
    public ExportArtifact pdf(UUID tailoredResumeId) throws ExportException {
        TailoredResume tailored = require(tailoredResumeId);
        assertExportable(tailoredResumeId);
        String html = render(modelOf(tailored));
        byte[] content = renderPdf(html);
        Path stored = store(exportsRoot, tailoredResumeId, "pdf", content);
        tailored.setHtml(html);
        tailored.setPdfPath(stored.toString());
        tailoredResumeRepository.save(tailored);
        return new ExportArtifact("application/pdf", "tailored-resume-" + tailoredResumeId + ".pdf", content);
    }

    @Transactional
    public ExportArtifact docx(UUID tailoredResumeId) throws ExportException {
        TailoredResume tailored = require(tailoredResumeId);
        assertExportable(tailoredResumeId);
        byte[] content = renderDocx(modelOf(tailored));
        Path stored = store(exportsRoot, tailoredResumeId, "docx", content);
        tailored.setDocxPath(stored.toString());
        tailoredResumeRepository.save(tailored);
        return new ExportArtifact(
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "tailored-resume-" + tailoredResumeId + ".docx", content);
    }

    /** LaTeX artifact (tailored edit workspace / Overleaf export): renders the same DocModel through the TEXT-mode {@code latex.tex} template. */
    @Transactional
    public ExportArtifact latex(UUID tailoredResumeId) throws ExportException {
        TailoredResume tailored = require(tailoredResumeId);
        assertExportable(tailoredResumeId);
        String tex = templateEngine.process("latex", new Context(Locale.ROOT, latexModelMap(modelOf(tailored))));
        byte[] content = tex.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        store(exportsRoot, tailoredResumeId, "tex", content);
        return new ExportArtifact(
                "application/x-tex",
                "tailored-resume-" + tailoredResumeId + ".tex", content);
    }

    // ------------------------------------------------------------------

    private static boolean validationValid(String stored) {
        try {
            return MAPPER.readTree(stored).path("valid").asBoolean(false);
        } catch (Exception ex) {
            return false;
        }
    }

    /** Build the document model from the tailored content JSONB (PRD §7.7). */
    private DocModel modelOf(TailoredResume tailored) {
        // After a full-document edit the saved document is authoritative; the
        // merged document (basics, summary, skills, experience, projects,
        // education) renders as-is. Before any edit, fall back to reconciling
        // master structured data + tailored content + change statuses.
        String documentJson = tailored.getDocument();
        if (documentJson != null && !documentJson.isBlank()) {
            return modelOfDocument(tailored, parse(documentJson));
        }
        return modelOfContent(tailored, parse(tailored.getContent()),
                parse(tailored.getResumeVersion().getStructuredData()));
    }

    private DocModel modelOfContent(TailoredResume tailored, JsonNode content, JsonNode resumeJson) {
        JsonNode basicsNode = resumeJson.path("basics");
        String name = basicsNode.path("name").asText("Candidate Resume");
        Basics basics = new Basics(
                name,
                basicsNode.path("email").asText(""),
                basicsNode.path("phone").asText(""),
                basicsNode.path("location").asText(""),
                basicsNode.path("linkedin").asText(""),
                basicsNode.path("github").asText("")
        );

        String summary = null;
        if (content.path("summary").isObject() && content.path("summary").has("tailored")) {
            summary = content.path("summary").path("tailored").asText();
            if (summary.isBlank()) {
                summary = null;
            }
        }
        if (summary == null && resumeJson.has("summary")) {
            summary = resumeJson.path("summary").asText(null);
        }

        List<Skill> skills = new ArrayList<>();
        if (resumeJson.path("skills").isArray()) {
            for (JsonNode s : resumeJson.path("skills")) {
                skills.add(new Skill(s.path("name").asText(""), s.path("category").asText("")));
            }
        }

        Map<String, String> tailoredBullets = new java.util.HashMap<>();
        if (content.path("experience").isArray()) {
            for (JsonNode entry : content.path("experience")) {
                if (entry.path("bullets").isArray()) {
                    for (JsonNode b : entry.path("bullets")) {
                        String orig = b.path("original_text").asText("");
                        String tail = b.path("tailored_text").asText("");
                        if (!orig.isBlank() && !tail.isBlank()) {
                            tailoredBullets.put(orig, tail);
                        }
                    }
                }
            }
        }

        List<Section> sections = new ArrayList<>();
        java.util.Set<String> rejectedOriginals = new java.util.HashSet<>();
        for (var row : tailoredChangeRepository.findByTailoredResumeIdOrderByCreatedAtAsc(tailored.getId())) {
            if ("REJECTED".equals(row.getStatus())) {
                rejectedOriginals.add(row.getOriginalText());
            }
        }

        if (resumeJson.path("experience").isArray()) {
            for (JsonNode entry : resumeJson.path("experience")) {
                String company = entry.path("company").asText("");
                String title = entry.path("title").asText("");
                String heading = join(company, title);
                List<String> bullets = new ArrayList<>();
                if (entry.path("bullets").isArray()) {
                    for (JsonNode b : entry.path("bullets")) {
                        String origText = b.isObject() ? b.path("text").asText("") : b.asText("");
                        if (!origText.isBlank()) {
                            String textToUse = rejectedOriginals.contains(origText)
                                    ? origText
                                    : tailoredBullets.getOrDefault(origText, origText);
                            bullets.add(textToUse);
                        }
                    }
                }
                if (!bullets.isEmpty()) {
                    sections.add(new Section(heading, bullets));
                }
            }
        }

        List<Project> projects = new ArrayList<>();
        if (resumeJson.path("projects").isArray()) {
            for (JsonNode p : resumeJson.path("projects")) {
                projects.add(new Project(p.path("name").asText(""), p.path("description").asText("")));
            }
        }

        List<Education> education = new ArrayList<>();
        if (resumeJson.path("education").isArray()) {
            for (JsonNode e : resumeJson.path("education")) {
                education.add(new Education(
                        e.path("institution").asText(""),
                        e.path("degree").asText(""),
                        e.path("field").asText(""),
                        e.path("end").asText("")
                ));
            }
        }

        return new DocModel(
                name,
                basics,
                summary,
                skills,
                sections,
                projects,
                education,
                tailored.getScoreBefore(),
                tailored.getScoreAfter(),
                STAMP.format(Instant.now()));
    }

    /**
     * Document-authoritative export: the saved merged document (PUT
     * /tailored/{id}/edit) is the single source of truth. Bullet text is used
     * verbatim; there is no content/change reconciliation.
     */
    private DocModel modelOfDocument(TailoredResume tailored, JsonNode document) {
        JsonNode basicsNode = document.path("basics");
        String name = basicsNode.path("name").asText("Candidate Resume");
        Basics basics = new Basics(
                name,
                basicsNode.path("email").asText(""),
                basicsNode.path("phone").asText(""),
                basicsNode.path("location").asText(""),
                basicsNode.path("linkedin").asText(""),
                basicsNode.path("github").asText("")
        );

        String summary = document.path("summary").asText(null);
        if (summary != null && summary.isBlank()) {
            summary = null;
        }

        List<Skill> skills = new ArrayList<>();
        if (document.path("skills").isArray()) {
            for (JsonNode s : document.path("skills")) {
                skills.add(new Skill(s.path("name").asText(""), s.path("category").asText("")));
            }
        }

        List<Section> sections = new ArrayList<>();
        if (document.path("experience").isArray()) {
            for (JsonNode entry : document.path("experience")) {
                String heading = join(entry.path("company").asText(""), entry.path("title").asText(""));
                List<String> bullets = new ArrayList<>();
                if (entry.path("bullets").isArray()) {
                    for (JsonNode b : entry.path("bullets")) {
                        String text = b.isObject() ? b.path("text").asText("") : b.asText("");
                        if (!text.isBlank()) {
                            bullets.add(text);
                        }
                    }
                }
                if (!bullets.isEmpty()) {
                    sections.add(new Section(heading, bullets));
                }
            }
        }

        List<Project> projects = new ArrayList<>();
        if (document.path("projects").isArray()) {
            for (JsonNode p : document.path("projects")) {
                projects.add(new Project(p.path("name").asText(""), p.path("description").asText("")));
            }
        }

        List<Education> education = new ArrayList<>();
        if (document.path("education").isArray()) {
            for (JsonNode e : document.path("education")) {
                education.add(new Education(
                        e.path("institution").asText(""),
                        e.path("degree").asText(""),
                        e.path("field").asText(""),
                        e.path("end").asText("")
                ));
            }
        }

        return new DocModel(
                name,
                basics,
                summary,
                skills,
                sections,
                projects,
                education,
                tailored.getScoreBefore(),
                tailored.getScoreAfter(),
                STAMP.format(Instant.now()));
    }

    private static java.util.Optional<JsonNode> findEntry(JsonNode experience, String id) {
        for (JsonNode entry : experience) {
            if (id.equals(entry.path("id").asText(""))) {
                return java.util.Optional.of(entry);
            }
        }
        return java.util.Optional.empty();
    }

    private static String join(String company, String title) {
        if (company.isBlank()) {
            return title;
        }
        if (title.isBlank()) {
            return company;
        }
        return company + ", " + title;
    }

    private String render(DocModel model) {
        return templateEngine.process("resume", new Context(Locale.ROOT, modelMap(model)));
    }

    private static Map<String, Object> modelMap(DocModel model) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("title", model.title());
        map.put("basics", model.basics());
        map.put("summary", model.summary());
        map.put("skills", model.skills());
        map.put("sections", model.sections());
        map.put("projects", model.projects());
        map.put("education", model.education());
        map.put("generated_at", model.generatedAt());
        map.put("score_before", model.scoreBefore());
        map.put("score_after", model.scoreAfter());
        return map;
    }

    /**
     * LaTeX-flavoured model map: every user string is escaped for the LaTeX
     * specials (\ & % $ # _ { } ~ ^) so the rendered .tex compiles cleanly.
     * Kept separate from {@link #modelMap} so the HTML template stays raw.
     */
    private static Map<String, Object> latexModelMap(DocModel model) {
        Map<String, Object> basics = new LinkedHashMap<>();
        if (model.basics() != null) {
            basics.put("email", latexEscape(model.basics().email()));
            basics.put("phone", latexEscape(model.basics().phone()));
            basics.put("location", latexEscape(model.basics().location()));
            basics.put("linkedin", latexEscape(model.basics().linkedin()));
            basics.put("github", latexEscape(model.basics().github()));
        } else {
            basics.put("email", "");
            basics.put("phone", "");
            basics.put("location", "");
            basics.put("linkedin", "");
            basics.put("github", "");
        }

        List<Map<String, Object>> skills = new ArrayList<>();
        for (Skill skill : model.skills() == null ? List.<Skill>of() : model.skills()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("name", latexEscape(skill.name()));
            entry.put("category", latexEscape(skill.category()));
            skills.add(entry);
        }

        List<Map<String, Object>> sections = new ArrayList<>();
        for (Section section : model.sections() == null ? List.<Section>of() : model.sections()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("heading", latexEscape(section.heading()));
            entry.put("bullets", section.bullets() == null ? List.<String>of()
                    : section.bullets().stream().map(ExportService::latexEscape).toList());
            sections.add(entry);
        }

        List<Map<String, Object>> projects = new ArrayList<>();
        for (Project project : model.projects() == null ? List.<Project>of() : model.projects()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("name", latexEscape(project.name()));
            entry.put("description", latexEscape(project.description()));
            projects.add(entry);
        }

        List<Map<String, Object>> education = new ArrayList<>();
        for (Education edu : model.education() == null ? List.<Education>of() : model.education()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("institution", latexEscape(edu.institution()));
            entry.put("degree", latexEscape(edu.degree()));
            entry.put("field", latexEscape(edu.field()));
            education.add(entry);
        }

        Map<String, Object> map = new LinkedHashMap<>();
        map.put("title", latexEscape(model.title()));
        map.put("basics", basics);
        map.put("summary", latexEscape(model.summary()));
        map.put("skills", skills);
        map.put("sections", sections);
        map.put("projects", projects);
        map.put("education", education);
        map.put("generated_at", model.generatedAt());
        return map;
    }

    private static String latexEscape(String text) {
        if (text == null) {
            return null;
        }
        return text.replace("\\", "\\textbackslash{}")
                .replace("&", "\\&")
                .replace("%", "\\%")
                .replace("$", "\\$")
                .replace("#", "\\#")
                .replace("_", "\\_")
                .replace("{", "\\{")
                .replace("}", "\\}")
                .replace("~", "\\textasciitilde{}")
                .replace("^", "\\textasciicircum{}");
    }

    private static byte[] renderPdf(String html) throws ExportException {
        ITextRenderer renderer = new ITextRenderer();
        renderer.setDocumentFromString(html);
        renderer.layout();
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            renderer.createPDF(out);
            return out.toByteArray();
        } catch (DocumentException | IOException ex) {
            throw new ExportException("Could not render PDF", ex);
        }
    }

    private static byte[] renderDocx(DocModel model) throws ExportException {
        try (XWPFDocument doc = new XWPFDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            heading(doc, model.title(), 16);
            if (model.basics() != null) {
                StringBuilder contact = new StringBuilder();
                if (model.basics().email() != null && !model.basics().email().isEmpty()) contact.append(model.basics().email());
                if (model.basics().phone() != null && !model.basics().phone().isEmpty()) contact.append(" | ").append(model.basics().phone());
                if (model.basics().location() != null && !model.basics().location().isEmpty()) contact.append(" | ").append(model.basics().location());
                if (model.basics().linkedin() != null && !model.basics().linkedin().isEmpty()) contact.append(" | ").append(model.basics().linkedin());
                if (model.basics().github() != null && !model.basics().github().isEmpty()) contact.append(" | ").append(model.basics().github());
                if (contact.length() > 0) meta(doc, contact.toString());
            }
            meta(doc, "Generated by ATS Doctor on " + model.generatedAt());

            if (model.summary() != null) {
                heading(doc, "Professional Summary", 12);
                body(doc, model.summary());
            }

            if (model.skills() != null && !model.skills().isEmpty()) {
                heading(doc, "Technical Skills", 12);
                StringBuilder sb = new StringBuilder();
                for (Skill s : model.skills()) {
                    if (sb.length() > 0) sb.append(", ");
                    sb.append(s.name());
                }
                body(doc, sb.toString());
            }

            if (model.sections() != null && !model.sections().isEmpty()) {
                heading(doc, "Work Experience", 12);
                for (Section section : model.sections()) {
                    heading(doc, section.heading(), 11);
                    for (String bullet : section.bullets()) {
                        bullet(doc, bullet);
                    }
                }
            }

            if (model.projects() != null && !model.projects().isEmpty()) {
                heading(doc, "Projects", 12);
                for (Project project : model.projects()) {
                    heading(doc, project.name(), 11);
                    if (project.description() != null && !project.description().isEmpty()) {
                        body(doc, project.description());
                    }
                }
            }

            if (model.education() != null && !model.education().isEmpty()) {
                heading(doc, "Education", 12);
                for (Education edu : model.education()) {
                    heading(doc, edu.institution(), 11);
                    body(doc, edu.degree() + " - " + edu.field());
                }
            }

            doc.write(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new ExportException("Could not render DOCX", ex);
        }
    }

    private static void heading(XWPFDocument doc, String text, int size) {
        XWPFParagraph p = doc.createParagraph();
        p.setAlignment(ParagraphAlignment.LEFT);
        XWPFRun run = p.createRun();
        run.setBold(true);
        run.setFontSize(size);
        run.setText(text);
    }

    private static void meta(XWPFDocument doc, String text) {
        XWPFParagraph p = doc.createParagraph();
        XWPFRun run = p.createRun();
        run.setItalic(true);
        run.setFontSize(9);
        run.setText(text);
    }

    private static void body(XWPFDocument doc, String text) {
        XWPFParagraph p = doc.createParagraph();
        p.createRun().setText(text);
    }

    private static void bullet(XWPFDocument doc, String text) {
        XWPFParagraph p = doc.createParagraph();
        p.setIndentationLeft(360);
        p.createRun().setText("\u2022 " + text);
    }

    private static Path store(Path root, UUID id, String ext, byte[] content) throws ExportException {
        try {
            Files.createDirectories(root);
            Path target = root.resolve(id + "." + ext).normalize();
            if (!target.getParent().equals(root)) {
                throw new ExportException("Unsafe export path for " + id);
            }
            Files.write(target, content);
            return target;
        } catch (IOException ex) {
            throw new ExportException("Could not store export for " + id, ex);
        }
    }

    private static JsonNode parse(String json) {
        if (json == null || json.isBlank()) {
            return MAPPER.createObjectNode();
        }
        try {
            return MAPPER.readTree(json);
        } catch (Exception ex) {
            return MAPPER.createObjectNode();
        }
    }

    private TailoredResume require(UUID tailoredResumeId) {
        return tailoredResumeRepository.findById(tailoredResumeId)
                .orElseThrow(() -> new TailoringNotFoundException(
                        "No tailored resume found for id " + tailoredResumeId));
    }

    /** Unchecked export failure marker (rendering/storage). */
    public static class ExportException extends RuntimeException {
        public ExportException(String message) {
            super(message);
        }

        public ExportException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}