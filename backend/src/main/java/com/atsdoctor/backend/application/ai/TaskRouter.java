package com.atsdoctor.backend.application.ai;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Maps application tasks → model profiles via ai-tasks.yml (PRD §6.2, DEC-004).
 * Profile → provider/model resolution stays in deployment configuration only.
 *
 * <p>Guards (TASK-013): deterministic tasks (exact_matching, scoring, extraction,
 * export) and unknown task ids are rejected — they are never AI-routable.
 */
@Component
public class TaskRouter {

    /** Deterministic steps that must never be routed to the AI layer (TASK-013). */
    public static final Set<String> DETERMINISTIC_TASKS = Set.of(
            "exact_matching", "scoring", "extraction", "export");

    private final Map<AiTask, ModelProfile> mappings = new LinkedHashMap<>();

    public TaskRouter(@Value("classpath:ai-tasks.yml") Resource aiTasksResource) {
        load(aiTasksResource);
    }

    public Optional<ModelProfile> profileFor(AiTask task) {
        return Optional.ofNullable(mappings.get(task));
    }

    /** Read-only snapshot of the current task → profile mapping. */
    public synchronized Map<AiTask, ModelProfile> mappings() {
        return Map.copyOf(mappings);
    }

    /** Apply a runtime task → profile change (PUT /ai/config, TASK-024). */
    public synchronized void updateProfile(AiTask task, ModelProfile profile) {
        if (task == null || profile == null) {
            throw new IllegalArgumentException("task and profile must not be null");
        }
        mappings.put(task, profile);
    }

    public boolean isRoutable(String taskId) {
        if (DETERMINISTIC_TASKS.contains(taskId)) {
            return false;
        }
        return AiTask.fromId(taskId).isPresent();
    }

    /** Resolve a raw task id, rejecting deterministic/unknown tasks. */
    public ModelProfile profileForTaskId(String taskId) {
        if (DETERMINISTIC_TASKS.contains(taskId)) {
            throw new IllegalArgumentException("Deterministic task is not AI-routable: " + taskId);
        }
        AiTask task = AiTask.fromId(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown AI task: " + taskId));
        return profileFor(task).orElse(task.defaultProfile());
    }

    private void load(Resource resource) {
        try (InputStream in = resource.getInputStream()) {
            Yaml yaml = new Yaml();
            Object root = yaml.load(in);
            if (root instanceof Map<?, ?> rootMap && rootMap.get("ai_tasks") instanceof Map<?, ?> taskMap) {
                for (Map.Entry<?, ?> entry : taskMap.entrySet()) {
                    AiTask.fromId(String.valueOf(entry.getKey())).ifPresent(task -> {
                        extractProfile(entry.getValue()).ifPresent(profile -> mappings.put(task, profile));
                    });
                }
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to load ai-tasks.yml", ex);
        }
    }

    private Optional<ModelProfile> extractProfile(Object config) {
        if (config instanceof Map<?, ?> map && map.get("profile") != null) {
            return ModelProfile.fromName(String.valueOf(map.get("profile")));
        }
        return Optional.empty();
    }
}
