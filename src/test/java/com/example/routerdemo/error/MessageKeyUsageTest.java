package com.example.routerdemo.error;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AssignableTypeFilter;

/**
 * Fails the build when the code uses a message key that no bundle defines, or passes a sentence where a key is
 * expected. Either way the client would otherwise get a generic message, and the gap would only show up in the logs.
 *
 * <p>The scan reads string literals, so it finds keys written directly in the code: any literal starting with
 * {@code error.}, and the first literal argument of every {@link ApiException} subclass, such as
 * {@code new NotFoundException("...")}. Comments are ignored. Keys built at runtime or held in variables are not
 * checked.
 */
class MessageKeyUsageTest {

    private static final List<String> BUNDLES = List.of("messages.properties", "messages_fr.properties");
    private static final Pattern KEY = Pattern.compile("^[a-z][A-Za-z0-9]*(\\.[A-Za-z0-9_]+)+$");

    private static Set<String> apiExceptionNames() {
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AssignableTypeFilter(ApiException.class));
        return scanner.findCandidateComponents("com.example.routerdemo").stream()
                .map(candidate -> candidate
                        .getBeanClassName()
                        .substring(candidate.getBeanClassName().lastIndexOf('.') + 1))
                .collect(Collectors.toSet());
    }

    private static Map<String, Set<String>> usedKeys() throws IOException {
        List<Pattern> patterns = List.of(
                Pattern.compile("\"(error\\.[^\"]*[^\".])\""), // a prefix ending in "." is concatenated
                Pattern.compile("\\b(?:" + String.join("|", apiExceptionNames()) + ")\\(\\s*\"([^\"]*)\""));
        Map<String, Set<String>> usages = new TreeMap<>();
        try (Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
            for (Path file : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                String text = withoutComments(Files.readString(file));
                for (Pattern pattern : patterns) {
                    Matcher matcher = pattern.matcher(text);
                    while (matcher.find()) {
                        usages.computeIfAbsent(matcher.group(1), k -> new TreeSet<>())
                                .add(file.getFileName().toString());
                    }
                }
            }
        }
        return usages;
    }

    /** Removes block comments (including Javadoc) and whole-line {@code //} comments. */
    private static String withoutComments(String source) {
        return source.replaceAll("(?s)/\\*.*?\\*/", "")
                .lines()
                .filter(line -> !line.trim().startsWith("//"))
                .collect(Collectors.joining("\n"));
    }

    @Test
    void everyMessageKeyUsedInTheCodeIsAKeyAndExistsInEveryBundle() throws IOException {
        Map<String, Set<String>> used = usedKeys();
        assertThat(used).as("the scan found too few keys, check the patterns").hasSizeGreaterThanOrEqualTo(8);
        assertThat(used.keySet())
                .as("Not message keys. Use a key and put details in internalMessage")
                .allMatch(key -> KEY.matcher(key).matches());
        for (String bundle : BUNDLES) {
            Set<String> defined = MessageBundleConsistencyTest.keys(bundle);
            assertThat(used.keySet()).as(bundle + " is missing keys").allMatch(defined::contains);
        }
    }
}
