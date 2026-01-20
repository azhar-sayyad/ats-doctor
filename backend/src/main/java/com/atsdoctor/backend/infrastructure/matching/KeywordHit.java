package com.atsdoctor.backend.infrastructure.matching;

import java.util.List;

/** JD keyword coverage scan (keywords category of the score). */
public record KeywordHit(String keyword, boolean matched, List<String> evidenceIds) {
}
