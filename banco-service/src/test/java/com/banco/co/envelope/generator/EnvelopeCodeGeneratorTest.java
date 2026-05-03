package com.banco.co.envelope.generator;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class EnvelopeCodeGeneratorTest {

    @Test
    void testGenerate_WhenCalled_ReturnsEnvelopeCodeWithSafeCharacters() {
        String code = EnvelopeCodeGenerator.generate();

        assertThat(code)
                .matches("ENV-" + LocalDate.now().getYear() + "-[ABCDEFGHJKLMNPQRSTUVWXYZ23456789]{10}");
    }
}
