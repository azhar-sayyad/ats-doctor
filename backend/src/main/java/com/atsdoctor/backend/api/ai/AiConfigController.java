package com.atsdoctor.backend.api.ai;

import com.atsdoctor.backend.application.ai.AiTask;
import com.atsdoctor.backend.application.ai.ModelProfile;
import com.atsdoctor.backend.application.ai.ModelProfileResolver;
import com.atsdoctor.backend.application.ai.ProfileConfig;
import com.atsdoctor.backend.application.ai.TaskRouter;
import com.atsdoctor.backend.application.config.AtsDoctorProperties;
import com.atsdoctor.backend.infrastructure.ai.AiProvider;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * FEAT-009 endpoints. GET /ai/config surfaces the task→profile mapping plus the
 * transparency payload (provider + "data leaves this machine", PRD §12.2).
 * PUT /ai/config validates profile names before applying (TASK-024).
 */
@RestController
@RequestMapping("/api/v1/ai")
public class AiConfigController {

    private final TaskRouter taskRouter;
    private final ModelProfileResolver profileResolver;
    private final AtsDoctorProperties properties;
    private final List<AiProvider> providers;

    public AiConfigController(TaskRouter taskRouter, ModelProfileResolver profileResolver,
                              AtsDoctorProperties properties, List<AiProvider> providers) {
        this.taskRouter = taskRouter;
        this.profileResolver = profileResolver;
        this.properties = properties;
        this.providers = providers;
    }

    @GetMapping("/config")
    public Map<String, Object> config() {
        return configPayload();
    }

    @PutMapping("/config")
    public Map<String, Object> updateConfig(@RequestBody Map<String, Object> body) {
        Object rawTasks = body.get("tasks");
        if (!(rawTasks instanceof Map<?, ?> taskMap)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request body must contain a 'tasks' object");
        }
        Map<AiTask, ModelProfile> updates = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : taskMap.entrySet()) {
            String taskId = String.valueOf(entry.getKey());
            String profileName = String.valueOf(entry.getValue());
            AiTask task = AiTask.fromId(taskId)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.BAD_REQUEST, "Unknown AI task: " + taskId));
            ModelProfile profile = ModelProfile.fromName(profileName)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.BAD_REQUEST, "Unknown profile '" + profileName + "' for task " + taskId));
            updates.put(task, profile);
        }
        updates.forEach(taskRouter::updateProfile);
        return configPayload();
    }

    @GetMapping("/models")
    public List<Map<String, Object>> models() {
        List<Map<String, Object>> profiles = new java.util.ArrayList<>();
        for (ModelProfile profile : ModelProfile.values()) {
            ProfileConfig config = profileResolver.resolve(profile);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("profile", profile.profileName());
            item.put("provider", config.provider());
            item.put("model", config.model());
            item.put("fallback", config.fallback() == null ? null : config.fallback().profileName());
            profiles.add(item);
        }
        return profiles;
    }

    private Map<String, Object> configPayload() {
        Map<String, Object> tasks = new LinkedHashMap<>();
        for (AiTask task : AiTask.values()) {
            tasks.put(task.id(),
                    Map.of("profile", taskRouter.profileFor(task).orElse(task.defaultProfile()).profileName()));
        }
        return Map.of(
                "mode", activeMode(),
                "provider", activeProvider(),
                "tasks", tasks,
                "data_leaves_machine", isExternal(),
                "note", "Transparency payload - PRD §12.2");
    }

    private String activeMode() {
        return properties.ai() == null || properties.ai().mode() == null ? "stub" : properties.ai().mode();
    }

    private String activeProvider() {
        return providers.stream()
                .map(AiProvider::name)
                .filter(name -> name.equals(activeMode()))
                .findFirst()
                .orElseGet(() -> providers.isEmpty() ? "none" : providers.get(0).name());
    }

    private boolean isExternal() {
        return "omniroute".equals(activeMode());
    }
}
