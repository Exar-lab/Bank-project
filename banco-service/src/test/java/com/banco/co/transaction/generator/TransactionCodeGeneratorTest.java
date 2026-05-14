package com.banco.co.transaction.generator;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TransactionCodeGeneratorTest {

    @Test
    void testGenerate_WhenCalled_ReturnsTransactionCodeWithExpectedFormat() {
        String code = TransactionCodeGenerator.generate();

        assertThat(code).matches("TXN-\\d{8}-\\d{8}");
    }
}
