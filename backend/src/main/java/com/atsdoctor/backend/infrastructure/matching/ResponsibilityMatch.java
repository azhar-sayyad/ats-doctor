package com.atsdoctor.backend.infrastructure.matching;

import java.util.List;

/** JD responsibility vs resume evidence (responsibilities category of the score). */
public record ResponsibilityMatch(String text, boolean matched, String matchType, List<String> evidenceIds) {
}
