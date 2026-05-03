package com.banco.co.transaction.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class TransactionLifecycleTest {

    @Test
    void testGenerateTransactionData_WhenTransactionCodeIsNull_FillsTransactionCode() {
        Transaction transaction = new Transaction();
        transaction.setAmount(new BigDecimal("100.00"));

        transaction.generateTransactionData();

        assertThat(transaction.getTransactionCode()).matches("TXN-\\d{8}-\\d{8}");
    }

    @Test
    void testGenerateTransactionData_WhenTransactionCodeAlreadyExists_PreservesExistingValue() {
        Transaction transaction = new Transaction();
        transaction.setAmount(new BigDecimal("100.00"));
        transaction.setTransactionCode("TXN-EXISTING-001");

        transaction.generateTransactionData();

        assertThat(transaction.getTransactionCode()).isEqualTo("TXN-EXISTING-001");
    }

    @Test
    void testGenerateTransactionData_WhenFeeIsNull_UsesDefaultZeroFeeForNetAmount() {
        Transaction transaction = new Transaction();
        transaction.setAmount(new BigDecimal("100.00"));

        transaction.generateTransactionData();

        assertThat(transaction.getNetAmount()).isEqualByComparingTo("100.00");
    }
}
