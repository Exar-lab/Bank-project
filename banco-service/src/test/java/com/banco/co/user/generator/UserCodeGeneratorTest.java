package com.banco.co.user.generator;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class UserCodeGeneratorTest {

    @Test
    void testGenerate_WhenCalled_ReturnsUserCodeWithSafeCharacters() {
        int currentYear = LocalDate.now().getYear();
        String code = UserCodeGenerator.generate();

        assertThat(code)
                .matches("USR-" + currentYear + "-[ABCDEFGHJKLMNPQRSTUVWXYZ23456789]{6}");
    }

    @Test
    void testGenerateUsername_WhenCalled_ReturnsNamePrefixedNumericUsername() {
        int currentYear = LocalDate.now().getYear();
        String username = UserCodeGenerator.generateUsername("Ana");

        assertThat(username)
                .matches("Ana-" + currentYear + "-\\d{10}");
    }
}
