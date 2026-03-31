package com.banco.co.exception.fraud;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

class FraudBlockedExceptionTest {

    @Test
    void testConstructor_ValidArguments_SetsMessageWithTransactionCodeAndReason() {
        FraudBlockedException ex = new FraudBlockedException("TXN-BCR-001", "Velocity limit exceeded");

        assertThat(ex.getMessage())
                .contains("TXN-BCR-001")
                .contains("Velocity limit exceeded");
    }

    @Test
    void testConstructor_ValidArguments_SetsErrorCodeToFraudBlocked() {
        FraudBlockedException ex = new FraudBlockedException("TXN-BCR-002", "Suspicious pattern");

        assertThat(ex.getErrorCode()).isEqualTo("FRAUD_BLOCKED");
    }

    @Test
    void testConstructor_ValidArguments_SetsHttpStatusToUnprocessableEntity() {
        FraudBlockedException ex = new FraudBlockedException("TXN-BCR-003", "High-risk merchant");

        assertThat(ex.getHttpStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void testConstructor_ValidArguments_AddsTransactionCodeMetadata() {
        FraudBlockedException ex = new FraudBlockedException("TXN-BCR-004", "Blocked by rule 42");

        assertThat(ex.getMetadata()).containsEntry("transactionCode", "TXN-BCR-004");
    }

    @Test
    void testConstructor_ValidArguments_AddsReasonMetadata() {
        FraudBlockedException ex = new FraudBlockedException("TXN-BCR-005", "Blocked by rule 42");

        assertThat(ex.getMetadata()).containsEntry("reason", "Blocked by rule 42");
    }

    @Test
    void testConstructor_ValidArguments_IsInstanceOfFraudException() {
        FraudBlockedException ex = new FraudBlockedException("TXN-BCR-006", "Some reason");

        assertThat(ex).isInstanceOf(FraudException.class);
    }

    @Test
    void testConstructor_ValidArguments_IsRuntimeException() {
        FraudBlockedException ex = new FraudBlockedException("TXN-BCR-007", "Some reason");

        assertThat(ex).isInstanceOf(RuntimeException.class);
    }
}
