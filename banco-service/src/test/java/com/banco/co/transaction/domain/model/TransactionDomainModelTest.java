package com.banco.co.transaction.domain.model;

import com.banco.co.transaction.enums.TransactionStatus;
import com.banco.co.transaction.exception.transaction.TransactionStatusException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pure domain model tests — ZERO JPA, ZERO Spring context.
 * Verifies business rules on com.banco.co.transaction.domain.model.Transaction.
 * Naming: test{Method}_{Condition}_{Expected}
 */
class TransactionDomainModelTest {

    private Transaction transaction;

    @BeforeEach
    void setUp() {
        transaction = new Transaction();
        transaction.setId(UUID.randomUUID());
        transaction.setTransactionCode("TXN-BCR-2024-X7K9P2M3");
        transaction.setAmount(new BigDecimal("1000.00"));
        transaction.setFee(BigDecimal.ZERO);
        transaction.setCurrency("CRC");
        transaction.setStatus(TransactionStatus.PENDING);
        transaction.setCreatedAt(LocalDateTime.now());
    }

    // ══════════════════════════════════════════════════════════
    //  process()
    // ══════════════════════════════════════════════════════════

    @Test
    void testProcess_WhenPending_TransitionsToProcessing() {
        transaction.process();

        assertThat(transaction.getStatus()).isEqualTo(TransactionStatus.PROCESSING);
    }

    @Test
    void testProcess_WhenPending_SetsProcessedAt() {
        transaction.process();

        assertThat(transaction.getProcessedAt()).isNotNull();
    }

    @Test
    void testProcess_WhenProcessing_ThrowsTransactionStatusException() {
        transaction.setStatus(TransactionStatus.PROCESSING);

        assertThatThrownBy(transaction::process)
                .isInstanceOf(TransactionStatusException.class);
    }

    @Test
    void testProcess_WhenCompleted_ThrowsTransactionStatusException() {
        transaction.setStatus(TransactionStatus.COMPLETED);

        assertThatThrownBy(transaction::process)
                .isInstanceOf(TransactionStatusException.class);
    }

    // ══════════════════════════════════════════════════════════
    //  complete()
    // ══════════════════════════════════════════════════════════

    @Test
    void testComplete_WhenProcessing_TransitionsToCompleted() {
        transaction.setStatus(TransactionStatus.PROCESSING);

        transaction.complete();

        assertThat(transaction.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
    }

    @Test
    void testComplete_WhenProcessing_SetsCompletedAt() {
        transaction.setStatus(TransactionStatus.PROCESSING);

        transaction.complete();

        assertThat(transaction.getCompletedAt()).isNotNull();
    }

    @Test
    void testComplete_WhenPending_ThrowsTransactionStatusException() {
        assertThatThrownBy(transaction::complete)
                .isInstanceOf(TransactionStatusException.class);
    }

    // ══════════════════════════════════════════════════════════
    //  completeFromApproved()
    // ══════════════════════════════════════════════════════════

    @Test
    void testCompleteFromApproved_WhenApproved_TransitionsToCompleted() {
        transaction.setStatus(TransactionStatus.APPROVED);

        transaction.completeFromApproved();

        assertThat(transaction.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
    }

    @Test
    void testCompleteFromApproved_WhenApproved_SetsCompletedAt() {
        transaction.setStatus(TransactionStatus.APPROVED);

        transaction.completeFromApproved();

        assertThat(transaction.getCompletedAt()).isNotNull();
    }

    @Test
    void testCompleteFromApproved_WhenProcessing_ThrowsTransactionStatusException() {
        transaction.setStatus(TransactionStatus.PROCESSING);

        assertThatThrownBy(transaction::completeFromApproved)
                .isInstanceOf(TransactionStatusException.class);
    }

    // ══════════════════════════════════════════════════════════
    //  fail()
    // ══════════════════════════════════════════════════════════

    @Test
    void testFail_WhenProcessing_TransitionsToFailed() {
        transaction.setStatus(TransactionStatus.PROCESSING);

        transaction.fail("Insufficient funds");

        assertThat(transaction.getStatus()).isEqualTo(TransactionStatus.FAILED);
    }

    @Test
    void testFail_WhenProcessing_SetsRejectionReason() {
        transaction.setStatus(TransactionStatus.PROCESSING);

        transaction.fail("Insufficient funds");

        assertThat(transaction.getRejectionReason()).isEqualTo("Insufficient funds");
    }

    @Test
    void testFail_WhenPending_ThrowsTransactionStatusException() {
        assertThatThrownBy(() -> transaction.fail("reason"))
                .isInstanceOf(TransactionStatusException.class);
    }

    // ══════════════════════════════════════════════════════════
    //  reverse()
    // ══════════════════════════════════════════════════════════

    @Test
    void testReverse_WhenCompleted_TransitionsToReversed() {
        transaction.setStatus(TransactionStatus.COMPLETED);

        transaction.reverse("Customer request");

        assertThat(transaction.getStatus()).isEqualTo(TransactionStatus.REVERSED);
    }

    @Test
    void testReverse_WhenCompleted_SetsReversalFields() {
        transaction.setStatus(TransactionStatus.COMPLETED);

        transaction.reverse("Customer request");

        assertThat(transaction.getReversedAt()).isNotNull();
        assertThat(transaction.getReversalReason()).isEqualTo("Customer request");
    }

    @Test
    void testReverse_WhenProcessing_ThrowsTransactionStatusException() {
        transaction.setStatus(TransactionStatus.PROCESSING);

        assertThatThrownBy(() -> transaction.reverse("reason"))
                .isInstanceOf(TransactionStatusException.class);
    }

    // ══════════════════════════════════════════════════════════
    //  flagForFraud()
    // ══════════════════════════════════════════════════════════

    @Test
    void testFlagForFraud_SetsAllFraudFields() {
        BigDecimal score = new BigDecimal("85.00");

        transaction.flagForFraud(score, "Suspicious pattern detected");

        assertThat(transaction.isFlaggedForFraud()).isTrue();
        assertThat(transaction.getFraudScore()).isEqualByComparingTo(score);
        assertThat(transaction.getFraudReason()).isEqualTo("Suspicious pattern detected");
        assertThat(transaction.getStatus()).isEqualTo(TransactionStatus.PENDING_REVIEW);
    }

    // ══════════════════════════════════════════════════════════
    //  approve()
    // ══════════════════════════════════════════════════════════

    @Test
    void testApprove_SetsApprovalFields() {
        transaction.approve("admin@banco.co");

        assertThat(transaction.getApprovedBy()).isEqualTo("admin@banco.co");
        assertThat(transaction.getApprovedAt()).isNotNull();
        assertThat(transaction.getStatus()).isEqualTo(TransactionStatus.APPROVED);
    }

    // ══════════════════════════════════════════════════════════
    //  isTransfer() / isDeposit() / isWithdrawal()
    // ══════════════════════════════════════════════════════════

    @Test
    void testIsTransfer_WhenBothAccountIds_ReturnsTrue() {
        transaction.setFromAccountId(UUID.randomUUID());
        transaction.setToAccountId(UUID.randomUUID());

        assertThat(transaction.isTransfer()).isTrue();
    }

    @Test
    void testIsDeposit_WhenOnlyToAccountId_ReturnsTrue() {
        transaction.setFromAccountId(null);
        transaction.setToAccountId(UUID.randomUUID());

        assertThat(transaction.isDeposit()).isTrue();
    }

    @Test
    void testIsWithdrawal_WhenOnlyFromAccountId_ReturnsTrue() {
        transaction.setFromAccountId(UUID.randomUUID());
        transaction.setToAccountId(null);

        assertThat(transaction.isWithdrawal()).isTrue();
    }

    @Test
    void testIsTransfer_WhenNoAccountIds_ReturnsFalse() {
        transaction.setFromAccountId(null);
        transaction.setToAccountId(null);

        assertThat(transaction.isTransfer()).isFalse();
    }

    // ══════════════════════════════════════════════════════════
    //  canBeReversed()
    // ══════════════════════════════════════════════════════════

    @Test
    void testCanBeReversed_WhenCompletedWithin90Days_ReturnsTrue() {
        transaction.setStatus(TransactionStatus.COMPLETED);
        transaction.setCreatedAt(LocalDateTime.now().minusDays(30));

        assertThat(transaction.canBeReversed()).isTrue();
    }

    @Test
    void testCanBeReversed_WhenCompletedAndAlreadyReversed_ReturnsFalse() {
        transaction.setStatus(TransactionStatus.COMPLETED);
        transaction.setCreatedAt(LocalDateTime.now().minusDays(10));
        transaction.setReversedAt(LocalDateTime.now());

        assertThat(transaction.canBeReversed()).isFalse();
    }

    @Test
    void testCanBeReversed_WhenOlderThan90Days_ReturnsFalse() {
        transaction.setStatus(TransactionStatus.COMPLETED);
        transaction.setCreatedAt(LocalDateTime.now().minusDays(100));

        assertThat(transaction.canBeReversed()).isFalse();
    }

    @Test
    void testCanBeReversed_WhenNotCompleted_ReturnsFalse() {
        transaction.setStatus(TransactionStatus.PROCESSING);
        transaction.setCreatedAt(LocalDateTime.now().minusDays(10));

        assertThat(transaction.canBeReversed()).isFalse();
    }

    // ══════════════════════════════════════════════════════════
    //  Spec requirement: cross-feature references are IDs only
    // ══════════════════════════════════════════════════════════

    @Test
    void testFromAccountId_IsUUID_NotEntityReference() {
        UUID accountId = UUID.randomUUID();
        transaction.setFromAccountId(accountId);

        assertThat(transaction.getFromAccountId()).isEqualTo(accountId);
    }

    @Test
    void testToAccountId_IsUUID_NotEntityReference() {
        UUID accountId = UUID.randomUUID();
        transaction.setToAccountId(accountId);

        assertThat(transaction.getToAccountId()).isEqualTo(accountId);
    }

    @Test
    void testCardId_IsUUID_NotEntityReference() {
        UUID cardId = UUID.randomUUID();
        transaction.setCardId(cardId);

        assertThat(transaction.getCardId()).isEqualTo(cardId);
    }

    @Test
    void testEnvelopeId_IsUUID_NotEntityReference() {
        UUID envelopeId = UUID.randomUUID();
        transaction.setEnvelopeId(envelopeId);

        assertThat(transaction.getEnvelopeId()).isEqualTo(envelopeId);
    }

    @Test
    void testOriginalTransactionId_IsUUID_NotEntityReference() {
        UUID originalId = UUID.randomUUID();
        transaction.setOriginalTransactionId(originalId);

        assertThat(transaction.getOriginalTransactionId()).isEqualTo(originalId);
    }

    // ══════════════════════════════════════════════════════════
    //  initializeTransactionData()
    // ══════════════════════════════════════════════════════════

    @Test
    void testInitializeTransactionData_WhenCodeIsNull_GeneratesTransactionCode() {
        Transaction fresh = new Transaction();
        fresh.setAmount(new BigDecimal("500.00"));
        fresh.setFee(BigDecimal.ZERO);

        fresh.initializeTransactionData();

        assertThat(fresh.getTransactionCode()).isNotNull();
        assertThat(fresh.getTransactionCode()).startsWith("TXN-");
    }

    @Test
    void testInitializeTransactionData_WhenCodeExists_KeepsExistingCode() {
        transaction.setTransactionCode("TXN-EXISTING-CODE");

        transaction.initializeTransactionData();

        assertThat(transaction.getTransactionCode()).isEqualTo("TXN-EXISTING-CODE");
    }

    @Test
    void testInitializeTransactionData_WhenNetAmountIsNull_CalculatesNetAmount() {
        transaction.setAmount(new BigDecimal("1000.00"));
        transaction.setFee(new BigDecimal("10.00"));
        transaction.setNetAmount(null);

        transaction.initializeTransactionData();

        assertThat(transaction.getNetAmount()).isEqualByComparingTo("990.00");
    }
}
