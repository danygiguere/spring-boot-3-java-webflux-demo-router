package com.example.routerdemo.web;

import com.example.routerdemo.exception.AppException;
import java.util.Locale;
import org.springframework.web.reactive.function.server.ServerRequest;

/** Typed, bounded reads of path and query parameters. Invalid values are a 400, never a 500. */
public final class Requests {

    public static final int DEFAULT_LIMIT = 20;
    public static final int MAX_LIMIT = 100;

    private Requests() {}

    /** The exchange locale from {@code LocaleContextResolver} — the same one the error handler uses. */
    public static Locale locale(ServerRequest request) {
        Locale locale = request.exchange().getLocaleContext().getLocale();
        return locale != null ? locale : Locale.ENGLISH;
    }

    /** A positive numeric path variable (an entity id). */
    public static long pathId(ServerRequest request, String name) {
        try {
            long value = Long.parseLong(request.pathVariable(name));
            if (value > 0) {
                return value;
            }
        } catch (NumberFormatException ignored) {
            // Reported as a 400 below.
        }
        throw new AppException.BadRequest("error.path.param.invalid", name);
    }

    /** Page size: {@value #DEFAULT_LIMIT} by default, never more than {@value #MAX_LIMIT}. */
    public static int limit(ServerRequest request) {
        return (int) queryLong(request, "limit", DEFAULT_LIMIT, 1, MAX_LIMIT);
    }

    /** Keyset cursor for newest-first lists: only ids strictly below it are returned. */
    public static long beforeId(ServerRequest request) {
        return queryLong(request, "beforeId", Long.MAX_VALUE, 1, Long.MAX_VALUE);
    }

    /** Keyset cursor for oldest-first lists: only ids strictly above it are returned. */
    public static long afterId(ServerRequest request) {
        return queryLong(request, "afterId", 0, 0, Long.MAX_VALUE);
    }

    private static long queryLong(ServerRequest request, String name, long defaultValue, long min, long max) {
        String raw = request.queryParam(name).orElse(null);
        if (raw == null) {
            return defaultValue;
        }
        try {
            long value = Long.parseLong(raw);
            if (value >= min && value <= max) {
                return value;
            }
        } catch (NumberFormatException ignored) {
            // Reported as a 400 below.
        }
        throw new AppException.BadRequest("error.query.param.invalid", name, min, max);
    }
}
