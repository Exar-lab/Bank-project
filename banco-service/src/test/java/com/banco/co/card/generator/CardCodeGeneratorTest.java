package com.banco.co.card.generator;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class CardCodeGeneratorTest {

    @Test
    void testGenerate_WhenCalled_ReturnsCardCodeWithSafeCharacters() {
        int currentYear = LocalDate.now().getYear();
        String code = CardCodeGenerator.generate();

        assertThat(code)
                .matches("CARD-" + currentYear + "-[ABCDEFGHJKLMNPQRSTUVWXYZ23456789]{6}");
    }
}
