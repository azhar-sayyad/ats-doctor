package com.atsdoctor.backend;

import com.atsdoctor.backend.infrastructure.export.ResumeTemplateCatalog;
import com.atsdoctor.backend.infrastructure.export.ResumeTemplateCatalog.ResumeTemplate;
import com.atsdoctor.backend.infrastructure.export.ResumeTemplateCatalog.UnknownResumeTemplateException;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** CL-020 — resume-templates.yml loads all templates with typed styles; the default is ats_clean. */
class ResumeTemplateCatalogTest {

    private final ResumeTemplateCatalog catalog =
            new ResumeTemplateCatalog(new ClassPathResource("resume-templates.yml"));

    @Test
    void loads_the_five_templates_in_file_order() {
        List<ResumeTemplate> templates = catalog.list();
        assertThat(templates).extracting(ResumeTemplate::slug)
                .containsExactly("ats_clean", "modern_minimal", "executive_classic",
                        "technical_compact", "academic_professional");
        assertThat(templates).allSatisfy(t -> {
            assertThat(t.name()).isNotBlank();
            assertThat(t.description()).isNotBlank();
            assertThat(t.html().bodyFont()).isNotBlank();
            assertThat(t.html().headingColor()).startsWith("#");
            assertThat(t.docx().headingColor()).isNotBlank();
            assertThat(t.latex().fontSize()).isBetween(9, 12);
            assertThat(t.latex().marginIn()).isGreaterThan(0);
        });
    }

    @Test
    void ats_clean_is_the_default_and_reproduces_the_original_look() {
        ResumeTemplate def = catalog.resolve(null);
        assertThat(def.slug()).isEqualTo("ats_clean");
        assertThat(def.html().bodySizePt()).isEqualTo(10.0f);
        assertThat(def.html().headingColor()).isEqualTo("#0f172a");
        assertThat(def.html().uppercaseTitle()).isTrue();
        assertThat(def.html().uppercaseHeadings()).isTrue();
        assertThat(def.docx().uppercaseHeadings()).isFalse();
        assertThat(def.latex().fontSize()).isEqualTo(10);
        assertThat(def.latex().marginIn()).isEqualTo(0.7f);
    }

    @Test
    void blank_or_unknown_slugs_resolve_to_the_default() {
        assertThat(catalog.resolve("").slug()).isEqualTo("ats_clean");
        assertThat(catalog.resolve("  ").slug()).isEqualTo("ats_clean");
        assertThat(catalog.resolve("does-not-exist")).isNull();
    }

    @Test
    void require_rejects_unknown_slugs() {
        assertThatThrownBy(() -> catalog.require("bogus"))
                .isInstanceOf(UnknownResumeTemplateException.class)
                .hasMessageContaining("Unknown resume template 'bogus'");
        assertThat(catalog.require("executive_classic").name()).contains("Executive");
    }
}
