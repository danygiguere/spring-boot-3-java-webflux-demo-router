package com.example.routerdemo.error;

/** What is known about the failed request. {@code path} never includes the query string, which may hold tokens. */
public record ErrorContext(String correlationId, String method, String path, int status, String code) {}
