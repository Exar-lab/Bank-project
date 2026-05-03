package com.banco.co.account.generator;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AccountNumberGeneratorTest {

    @Test
    void testGenerate_WhenCalled_ReturnsTwelveNumericDigits() {
        String accountNumber = AccountNumberGenerator.generate();

        assertThat(accountNumber).matches("\\d{12}");
    }
}
