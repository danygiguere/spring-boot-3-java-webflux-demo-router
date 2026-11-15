package com.example.routerdemo.error;

import java.util.List;
import org.springframework.http.HttpStatus;

/**
 * Base class for every error the application throws on purpose.
 *
 * <p>Each exception carries two separate messages:
 *
 * <ul>
 *   <li><b>Public message</b>: {@link #getMessageKey()} plus {@link #getPublicArgs()}. The handler looks the key up in
 *       {@code messages*.properties} for the caller's locale. This text is the only one sent to the client, so the key
 *       must always have a translation.
 *   <li><b>Internal message</b>: {@code internalMessage}, which becomes {@link Throwable#getMessage()}. It is written
 *       to the server logs and never sent to the client. It may contain identifiers, SQL fragments, upstream errors,
 *       and similar details. It is not translated, because only operators read it.
 * </ul>
 *
 * <p>Rules for callers:
 *
 * <ul>
 *   <li>Put in {@code publicArgs} only values that are safe to show the caller, such as limits or names the caller
 *       already supplied. Never put secrets, tokens, other users' data, or internal IDs in them.
 *   <li>Keep the original exception as {@code cause} when you wrap one, so the stack trace stays in the logs.
 *   <li>When a resource exists but belongs to someone else and its existence is private, throw
 *       {@link NotFoundException}, not {@link ForbiddenException}. A 403 would confirm that the resource exists.
 * </ul>
 */
public abstract class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String messageKey;
    private final List<Object> publicArgs;

    protected ApiException(
            HttpStatus status, String messageKey, List<?> publicArgs, String internalMessage, Throwable cause) {
        super(internalMessage != null ? internalMessage : messageKey, cause);
        this.status = status;
        this.messageKey = messageKey;
        this.publicArgs = List.copyOf(publicArgs);
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getMessageKey() {
        return messageKey;
    }

    public List<Object> getPublicArgs() {
        return publicArgs;
    }
}
