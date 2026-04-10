package com.atsdoctor.backend.infrastructure.export;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Hardcoded resume export template catalog (CL-020) loaded from the classpath
 * {@code resume-templates.yml} — same pattern as {@code ai-tasks.yml} in the
 * AI layer. The catalog only describes styles: rendering always goes through
 * the single {@code templates/export/resume.html} / {@code latex.tex}
 * templates with the selected template's values inlined. {@code ats_clean} is
 * the default and reproduces the pre-template rendering.
 *
 * <p>The DB stores only the chosen slug per tailored resume
 * ({@code tailored_resumes.template}); the definitions themselves never leave
 * the application, so tuning a template is a code change (and a test run), not
 * a migration.
 */
@Component
public class ResumeTemplateCatalog {

    public static final String DEFAULT_TEMPLATE = "ats_clean";

    private final Map<String, ResumeTemplate> templates = new LinkedHashMap<>();

    public ResumeTemplateCatalog(@Value("classpath:resume-templates.yml") Resource resource) {
        load(resource);
    }

    /** Read-only snapshot of the catalog in file order. */
    public List<ResumeTemplate> list() {
        return List.copyOf(templates.values());
    }

    public Optional<ResumeTemplate> get(String slug) {
        return Optional.ofNullable(templates.get(slug));
    }

    /** Resolve a slug or fall back to {@link #DEFAULT_TEMPLATE} for null/blank. */
    public ResumeTemplate resolve(String slug) {
        if (slug == null || slug.isBlank()) {
            return templates.get(DEFAULT_TEMPLATE);
        }
        return templates.get(slug);
    }

    /**
     * Strict lookup for user input: unknown slugs are rejected (400) rather
     * than silently falling back, so a typo never mangles an export.
     */
    public ResumeTemplate require(String slug) {
        ResumeTemplate template = templates.get(slug);
        if (template == null) {
            throw new UnknownResumeTemplateException(
                    "Unknown resume template '" + slug + "' (available: " + String.join(", ", templates.keySet()) + ")");
        }
        return template;
    }

    // ------------------------------------------------------------------

    /** One hardcoded template definition; styles are typed per format. */
    public record ResumeTemplate(String slug, String name, String description,
                                 HtmlStyle html, DocxStyle docx, LatexStyle latex) {
    }

    /** CSS-driving values inlined into the single export HTML template. */
    public record HtmlStyle(String bodyFont, String headingFont, float bodySizePt, float titleSizePt,
                            float sectionSizePt, float headingSizePt, float bodyLineHeight, float letterSpacing,
                            String textColor, String headingColor, String titleRuleColor, String sectionRuleColor,
                            String contactColor, String metaColor, String badgeBg, String badgeBorder,
                            boolean uppercaseTitle, boolean uppercaseHeadings) {
    }

    /** Fonts/colors for the POI DOCX writer. */
    public record DocxStyle(String bodyFont, String headingFont, float bodySizePt, float titleSizePt,
                            float sectionSizePt, float headingSizePt, String headingColor,
                            boolean uppercaseHeadings) {
    }

    /** Font size (pt) and page margin (in) applied to the .tex documentclass/geometry. */
    public record LatexStyle(int fontSize, float marginIn) {
    }

    /** Unknown slug in a user-supplied template value — mapped to 400. */
    public static class UnknownResumeTemplateException extends RuntimeException {
        public UnknownResumeTemplateException(String message) {
            super(message);
        }
    }

    private void load(Resource resource) {
        try (InputStream in = resource.getInputStream()) {
            Yaml yaml = new Yaml();
            Object root = yaml.load(in);
            if (root instanceof Map<?, ?> rootMap && rootMap.get("resume_templates") instanceof Map<?, ?> map) {
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    String slug = String.valueOf(entry.getKey());
                    if (entry.getValue() instanceof Map<?, ?> def) {
                        templates.put(slug, parseTemplate(slug, def));
                    }
                }
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to load resume-templates.yml", ex);
        }
        if (templates.isEmpty() || !templates.containsKey(DEFAULT_TEMPLATE)) {
            throw new IllegalStateException("resume-templates.yml must define the default template '" + DEFAULT_TEMPLATE + "'");
        }
    }

    private static ResumeTemplate parseTemplate(String slug, Map<?, ?> def) {
        String name = str(def, "name", slug);
        String description = str(def, "description", "");
        Map<?, ?> html = map(def, "html");
        Map<?, ?> docx = map(def, "docx");
        Map<?, ?> latex = map(def, "latex");
        HtmlStyle h = new HtmlStyle(
                str(html, "body_font", "'Helvetica Neue', Helvetica, Arial, sans-serif"),
                str(html, "heading_font", "'Helvetica Neue', Helvetica, Arial, sans-serif"),
                f(html, "body_size_pt", 10), f(html, "title_size_pt", 18),
                f(html, "section_size_pt", 12), f(html, "heading_size_pt", 10.5f),
                f(html, "body_line_height", 1.4f), f(html, "letter_spacing", 0.5f),
                str(html, "text_color", "#1e293b"), str(html, "heading_color", "#0f172a"),
                str(html, "title_rule_color", "#0f172a"), str(html, "section_rule_color", "#cbd5e1"),
                str(html, "contact_color", "#475569"), str(html, "meta_color", "#94a3b8"),
                str(html, "badge_bg", "#f1f5f9"), str(html, "badge_border", "#e2e8f0"),
                bool(html, "uppercase_title", true), bool(html, "uppercase_headings", true));
        DocxStyle d = new DocxStyle(
                str(docx, "body_font", "Helvetica"), str(docx, "heading_font", "Helvetica"),
                f(docx, "body_size_pt", 11), f(docx, "title_size_pt", 16),
                f(docx, "section_size_pt", 12), f(docx, "heading_size_pt", 11),
                str(docx, "heading_color", "000000"), bool(docx, "uppercase_headings", false));
        LatexStyle l = new LatexStyle(i(latex, "font_size", 10), f(latex, "margin_in", 0.7f));
        return new ResumeTemplate(slug, name, description, h, d, l);
    }

    @SuppressWarnings("unchecked")
    private static Map<?, ?> map(Map<?, ?> parent, String key) {
        Object value = parent.get(key);
        return value instanceof Map<?, ?> m ? m : Map.of();
    }

    private static String str(Map<?, ?> map, String key, String fallback) {
        Object value = map.get(key);
        return value == null ? fallback : String.valueOf(value);
    }

    private static float f(Map<?, ?> map, String key, float fallback) {
        Object value = map.get(key);
        if (value instanceof Number n) {
            return n.floatValue();
        }
        if (value != null) {
            try {
                return Float.parseFloat(String.valueOf(value));
            } catch (NumberFormatException ignored) {
                // fall through
            }
        }
        return fallback;
    }

    private static int i(Map<?, ?> map, String key, int fallback) {
        Object value = map.get(key);
        if (value instanceof Number n) {
            return n.intValue();
        }
        if (value != null) {
            try {
                return Integer.parseInt(String.valueOf(value));
            } catch (NumberFormatException ignored) {
                // fall through
            }
        }
        return fallback;
    }

    private static boolean bool(Map<?, ?> map, String key, boolean fallback) {
        Object value = map.get(key);
        return value instanceof Boolean b ? b : fallback;
    }

    /** CSV of slugs for error messages / docs. */
    public List<String> slugs() {
        return new ArrayList<>(templates.keySet());
    }
}
