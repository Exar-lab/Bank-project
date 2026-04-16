package com.banco.co.notification.email.template;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class EmailTemplateSecurityPolicyTest {

    @Test
    void testTemplates_WhenScanningEmailTemplates_ThenThUtextIsAbsent() throws IOException {
        Path emailTemplatesPath = Path.of("src", "main", "resources", "templates", "email");
        try (var stream = Files.walk(emailTemplatesPath)) {
            List<Path> htmlFiles = stream
                    .filter(path -> path.toString().endsWith(".html"))
                    .toList();

            assertThat(htmlFiles).isNotEmpty();
            for (Path htmlFile : htmlFiles) {
                String content = Files.readString(htmlFile).toLowerCase(Locale.ROOT);
                assertThat(content).doesNotContain("th:utext");
            }
        }
    }
}
