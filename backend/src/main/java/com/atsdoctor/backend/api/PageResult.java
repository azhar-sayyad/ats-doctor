package com.atsdoctor.backend.api;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Shared paginated-list envelope for the GET list endpoints
 * ({@code /jobs}, {@code /analyses}, {@code /tailored}): a bare array is a
 * contract change, so every list response is
 * {@code {"items": [...], "page": p, "size": s, "total": n, "has_more": bool}}
 * with items ordered newest-first. {@code page} is 0-based.
 */
public record PageResult<T>(List<T> items, int page, int size, long total, boolean hasMore) {

    public static final int DEFAULT_SIZE = 10;
    public static final int MAX_SIZE = 1000;

    /** Clamp a client-supplied page to a non-negative value. */
    public static int clampPage(int page) {
        return Math.max(page, 0);
    }

    /** Clamp a client-supplied size into {@code [1, MAX_SIZE]}. */
    public static int clampSize(int size) {
        return Math.min(Math.max(size, 1), MAX_SIZE);
    }

    public Map<String, Object> toMap() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("items", items);
        out.put("page", page);
        out.put("size", size);
        out.put("total", total);
        out.put("has_more", hasMore);
        return out;
    }
}
