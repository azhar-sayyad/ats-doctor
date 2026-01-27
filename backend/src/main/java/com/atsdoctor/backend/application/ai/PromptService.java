package com.atsdoctor.backend.application.ai;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Loads and renders versioned prompt templates from resources/prompts/
 * (PRD §6.5, DEC-007). Template filename is tracked per AiTask for ai_runs.
 * Unresolved {{variables}} surface as a clear error (TASK-014).
 */
@Service
public class PromptService {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{([^{}]+)}}");

    private final Map<AiTask, String> cache = new HashMap<>();

    public String render(AiTask task, Map<String, Object> variables) {
        String template = cache.computeIfAbsent(task, this::load);
        String rendered = template;
        for (Map.Entry<String, Object> entry : variables.entrySet()) {
            rendered = rendered.replace("{{" + entry.getKey() + "}}", String.valueOf(entry.getValue()));
        }
        assertNoMissingVariables(task, rendered);
        return rendered;
    }

    private void assertNoMissingVariables(AiTask task, String rendered) {
        List<String> missing = new ArrayList<>();
        Matcher matcher = PLACEHOLDER.matcher(rendered);
        while (matcher.find()) {
            String name = matcher.group(1).trim();
            if (!missing.contains(name)) {
                missing.add(name);
            }
        }
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException(
                    "Prompt template for task '" + task.id() + "' is missing variables: " + missing);
        }
    }

    private String load(AiTask task) {
        if (task.promptFile() == null) {
            throw new IllegalStateException("No prompt template defined for task: " + task.id());
        }
        ClassPathResource resource = new ClassPathResource("prompts/" + task.promptFile());
        try {
            return resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("Missing prompt template: " + task.promptFile(), ex);
        }
    }
}
