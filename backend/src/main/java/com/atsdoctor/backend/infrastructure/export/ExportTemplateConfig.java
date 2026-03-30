package com.atsdoctor.backend.infrastructure.export;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.util.Set;

/**
 * Standalone Thymeleaf engine for the tailored-resume export renderer
 * (SPRINT-06, FEAT-037, TASK-079). A dedicated engine with a scoped
 * {@code templates/export/} resolver keeps the export render decoupled from
 * any server-rendered UI engine. Two resolvers: HTML mode for the PDF/DOCX
 * source ({@code resume.html}) and TEXT mode for the LaTeX artifact
 * ({@code latex.tex}) — separated by {@code resolvablePatterns} so each
 * template name resolves to exactly one resolver.
 */
@Configuration
public class ExportTemplateConfig {

    @Bean
    public SpringTemplateEngine exportTemplateEngine() {
        ClassLoaderTemplateResolver html = new ClassLoaderTemplateResolver();
        html.setPrefix("templates/export/");
        html.setSuffix(".html");
        html.setTemplateMode("HTML");
        html.setResolvablePatterns(Set.of("resume"));
        html.setCharacterEncoding("UTF-8");

        ClassLoaderTemplateResolver tex = new ClassLoaderTemplateResolver();
        tex.setPrefix("templates/export/");
        tex.setSuffix(".tex");
        tex.setTemplateMode("TEXT");
        tex.setResolvablePatterns(Set.of("latex"));
        tex.setCharacterEncoding("UTF-8");

        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolvers(Set.of(html, tex));
        return engine;
    }
}