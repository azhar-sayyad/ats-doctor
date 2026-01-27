package com.atsdoctor.backend.application.ai;

import java.util.Optional;

public enum AiTask {
    RESUME_PARSER("resume_parser", "resume-parser-v1.txt", ModelProfile.CHEAP),
    JD_PARSER("jd_parser", "jd-parser-v1.txt", ModelProfile.CHEAP),
    REQUIREMENT_EXTRACTION("requirement_extraction", "requirement-extraction-v1.txt", ModelProfile.CHEAP),
    RESUME_TAILORING("resume_tailoring", "tailor-bullet-v1.txt", ModelProfile.QUALITY),
    FACT_VALIDATION("fact_validation", "validator-v1.txt", ModelProfile.QUALITY),
    EMBEDDING("embedding", null, ModelProfile.LOCAL);

    private final String id;
    private final String promptFile;
    private final ModelProfile defaultProfile;

    AiTask(String id, String promptFile, ModelProfile defaultProfile) {
        this.id = id;
        this.promptFile = promptFile;
        this.defaultProfile = defaultProfile;
    }

    public String id() {
        return id;
    }

    public String promptFile() {
        return promptFile;
    }

    /** Prompt version recorded in ai_runs / metadata, e.g. `resume-parser-v1`. */
    public String promptVersion() {
        if (promptFile == null) {
            return null;
        }
        return promptFile.endsWith(".txt") ? promptFile.substring(0, promptFile.length() - 4) : promptFile;
    }

    public ModelProfile defaultProfile() {
        return defaultProfile;
    }

    public static Optional<AiTask> fromId(String id) {
        for (AiTask task : values()) {
            if (task.id.equals(id)) {
                return Optional.of(task);
            }
        }
        return Optional.empty();
    }
}
