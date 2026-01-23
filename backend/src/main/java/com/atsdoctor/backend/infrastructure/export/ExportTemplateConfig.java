package com.atsdoctor.backend.infrastructure.export;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

/**
 * Standalone Thymeleaf engine for the tailored-resume export renderer
 * (SPRINT-06, FEAT-037, TASK-079). A dedicated engine with a scoped
 * {@code templates/export/} resolver keeps the export render decoupled from
 * any server-rendered UI engine.
 */
@Configuration
public class ExportTemplateConfig {

    @Bean
    public SpringTemplateEngine exportTemplateEngine() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/export/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode("HTML");
        resolver.setCharacterEncoding("UTF-8");

        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        return engine;
    }
}