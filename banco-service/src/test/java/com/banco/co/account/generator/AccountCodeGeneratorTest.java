package com.banco.co.account.generator;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class AccountCodeGeneratorTest {

    @Test
    void testGenerate_WhenCalled_ReturnsAccountCodeWithExpectedFormat() {
        int currentYear = LocalDate.now().getYear();
        String code = AccountCodeGenerator.generate();

        assertThat(code)
                .matches("CR-" + currentYear + "-\\d{20}");
    }
}
