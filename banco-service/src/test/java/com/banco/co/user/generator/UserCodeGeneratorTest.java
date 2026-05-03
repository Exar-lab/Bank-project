package com.banco.co.user.generator;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class UserCodeGeneratorTest {

    @Test
    void testGenerate_WhenCalled_ReturnsUserCodeWithSafeCharacters() {
        String code = UserCodeGenerator.generate();

        assertThat(code)
                .matches("USR-" + LocalDate.now().getYear() + "-[ABCDEFGHJKLMNPQRSTUVWXYZ23456789]{6}");
    }

    @Test
    void testGenerateUsername_WhenCalled_ReturnsNamePrefixedNumericUsername() {
        String username = UserCodeGenerator.generateUsername("Ana");

        assertThat(username)
                .matches("Ana-" + LocalDate.now().getYear() + "-\\d{10}");
    }
}
