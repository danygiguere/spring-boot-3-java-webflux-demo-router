package com.example.routerdemo.error;

import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

/**
 * Looks up public messages in {@code messages*.properties}.
 *
 * <p>A missing key returns null instead of the key itself, so callers fall back to a generic message and a raw key
 * never reaches the client. The gap is logged so it gets fixed.
 */
@Component
public class MessageTranslator {

    private static final Logger log = LoggerFactory.getLogger(MessageTranslator.class);

    private final MessageSource messages;

    public MessageTranslator(MessageSource messages) {
        this.messages = messages;
    }

    public String translate(String key, List<Object> args, Locale locale) {
        String text = messages.getMessage(key, args.toArray(), null, locale);
        if (text == null) {
            log.warn("Missing translation key={} locale={}", key, locale);
        }
        return text;
    }

    /** The message used when a validation error has no translation of its own. */
    public String invalidValue(Locale locale) {
        String text = translate("error.validation.invalidValue", List.of(), locale);
        return text != null ? text : "Invalid value.";
    }
}
