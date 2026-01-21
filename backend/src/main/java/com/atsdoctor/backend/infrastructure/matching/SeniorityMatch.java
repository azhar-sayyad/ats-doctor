package com.atsdoctor.backend.infrastructure.matching;

/** Seniority token alignment between the JD and the resume's experience titles. */
public record SeniorityMatch(String jdSeniority, String resumeSeniority, boolean aligned) {
}
