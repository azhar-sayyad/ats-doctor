package com.atsdoctor.backend.application.ai;

import java.util.Optional;

public enum ModelProfile {
    CHEAP("cheap"),
    QUALITY("quality"),
    LOCAL("local"),
    PRIVATE("private");

    private final String name;

    ModelProfile(String name) {
        this.name = name;
    }

    public String profileName() {
        return name;
    }

    public static Optional<ModelProfile> fromName(String name) {
        for (ModelProfile profile : values()) {
            if (profile.name.equalsIgnoreCase(name)) {
                return Optional.of(profile);
            }
        }
        return Optional.empty();
    }
}
