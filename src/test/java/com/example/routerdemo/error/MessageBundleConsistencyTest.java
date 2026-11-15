package com.example.routerdemo.error;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Fails the build when a message key is missing from one of the supported locales. */
class MessageBundleConsistencyTest {

    static Set<String> keys(String name) throws IOException {
        Properties properties = new Properties();
        try (var reader = new InputStreamReader(
                MessageBundleConsistencyTest.class.getClassLoader().getResourceAsStream(name),
                StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        return properties.stringPropertyNames();
    }

    @Test
    void everyLocaleDefinesTheSameKeys() throws IOException {
        assertThat(keys("messages_fr.properties")).containsExactlyInAnyOrderElementsOf(keys("messages.properties"));
    }
}
