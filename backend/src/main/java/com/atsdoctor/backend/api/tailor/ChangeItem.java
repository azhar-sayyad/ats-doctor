package com.atsdoctor.backend.api.tailor;

import com.atsdoctor.backend.infrastructure.persistence.TailoredChange;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * tailored_change listing item (07-api-contract §4, FEAT-037/036): the
 * read-only per-change record the traceability drawer consumes (TASK-073).
 * The review lifecycle (accept/reject/edit/regenerate) joins with FEAT-037
 * (TASK-074). snake_case JSON via toMap.
 */
public record ChangeItem(
        UUID id,
        String originalText,
        String tailoredText,
        String reason,
        String claimCategory,
        String status,
        String promptVersion,
        Instant createdAt) {

    public static ChangeItem of(TailoredChange change) {
        return new ChangeItem(
                change.getId(),
                change.getOriginalText(),
                change.getTailoredText(),
                change.getReason(),
                change.getClaimCategory(),
                change.getStatus(),
                change.getPromptVersion(),
                change.getCreatedAt());
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", id);
        map.put("original_text", originalText);
        map.put("tailored_text", tailoredText);
        map.put("reason", reason);
        map.put("claim_category", claimCategory);
        map.put("status", status);
        map.put("prompt_version", promptVersion);
        map.put("created_at", createdAt);
        return map;
    }
}