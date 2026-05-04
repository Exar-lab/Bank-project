package com.banco.co.envelope.generator;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class EnvelopeCodeGeneratorTest {

    @Test
    void testGenerate_WhenCalled_ReturnsEnvelopeCodeWithSafeCharacters() {
        int currentYear = LocalDate.now().getYear();
        String code = EnvelopeCodeGenerator.generate();

        assertThat(code)
                .matches("ENV-" + currentYear + "-[ABCDEFGHJKLMNPQRSTUVWXYZ23456789]{10}");
    }
}
