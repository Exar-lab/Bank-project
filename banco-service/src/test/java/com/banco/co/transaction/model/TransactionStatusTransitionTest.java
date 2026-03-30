package com.banco.co.transaction.model;

import com.banco.co.transaction.enums.TransactionStatus;
import com.banco.co.transaction.exception.transaction.TransactionStatusException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pure domain tests — no Spring context, no mocks.
 * Covers completeFromApproved(), flagForFraud(), and fail() state transitions.
 */
class TransactionStatusTransitionTest {

    // ══════════════════════════════════════════════════════════
    //  completeFromApproved()
    // ══════════════════════════════════════════════════════════

    @Test
    void testCompleteFromApproved_WhenApproved_SetsStatusToCompleted() {
        Transaction tx = buildApprovedTransaction();

        tx.completeFromApproved();

        assertThat(tx.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
    }

    @Test
    void testCompleteFromApproved_WhenApproved_SetsCompletedAtTimestamp() {
        Transaction tx = buildApprovedTransaction();

        tx.completeFromApproved();

        assertThat(tx.getCompletedAt()).isNotNull();
    }

    @Test
    void testCompleteFromApproved_WhenProcessing_ThrowsTransactionStatusException() {
        Transaction tx = new Transaction();
        tx.setStatus(TransactionStatus.PROCESSING);

        assertThatThrownBy(tx::completeFromApproved)
                .isInstanceOf(TransactionStatusException.class);
    }

    @Test
    void testCompleteFromApproved_WhenCompleted_ThrowsTransactionStatusException() {
        Transaction tx = new Transaction();
        tx.setStatus(TransactionStatus.COMPLETED);

        assertThatThrownBy(tx::completeFromApproved)
                .isInstanceOf(TransactionStatusException.class);
    }

    @Test
    void testCompleteFromApproved_WhenPending_ThrowsTransactionStatusException() {
        Transaction tx = new Transaction();
        tx.setStatus(TransactionStatus.PENDING);

        assertThatThrownBy(tx::completeFromApproved)
                .isInstanceOf(TransactionStatusException.class);
    }

    @Test
    void testCompleteFromApproved_WhenPendingReview_ThrowsTransactionStatusException() {
        Transaction tx = new Transaction();
        tx.setStatus(TransactionStatus.PENDING_REVIEW);

        assertThatThrownBy(tx::completeFromApproved)
                .isInstanceOf(TransactionStatusException.class);
    }

    // ══════════════════════════════════════════════════════════
    //  flagForFraud()
    // ══════════════════════════════════════════════════════════

    @Test
    void testFlagForFraud_WhenCalled_SetsStatusToPendingReview() {
        Transaction tx = new Transaction();

        tx.flagForFraud(BigDecimal.valueOf(75), "Suspicious activity");

        assertThat(tx.getStatus()).isEqualTo(TransactionStatus.PENDING_REVIEW);
    }

    @Test
    void testFlagForFraud_WhenCalled_SetsFlaggedForFraudToTrue() {
        Transaction tx = new Transaction();

        tx.flagForFraud(BigDecimal.valueOf(75), "Suspicious activity");

        assertThat(tx.isFlaggedForFraud()).isTrue();
    }

    @Test
    void testFlagForFraud_WhenCalled_SetsFraudScore() {
        Transaction tx = new Transaction();
        BigDecimal score = BigDecimal.valueOf(75);

        tx.flagForFraud(score, "Suspicious activity");

        assertThat(tx.getFraudScore()).isEqualByComparingTo(score);
    }

    @Test
    void testFlagForFraud_WhenCalled_SetsFraudReason() {
        Transaction tx = new Transaction();

        tx.flagForFraud(BigDecimal.valueOf(75), "Velocity limit exceeded");

        assertThat(tx.getFraudReason()).isEqualTo("Velocity limit exceeded");
    }

    // ══════════════════════════════════════════════════════════
    //  fail()
    // ══════════════════════════════════════════════════════════

    @Test
    void testFail_WhenProcessing_SetsStatusToFailed() {
        Transaction tx = new Transaction();
        tx.setStatus(TransactionStatus.PROCESSING);

        tx.fail("Blocked by fraud detection engine");

        assertThat(tx.getStatus()).isEqualTo(TransactionStatus.FAILED);
    }

    @Test
    void testFail_WhenProcessing_SetsRejectionReason() {
        Transaction tx = new Transaction();
        tx.setStatus(TransactionStatus.PROCESSING);

        tx.fail("Blocked by fraud detection engine");

        assertThat(tx.getRejectionReason()).isEqualTo("Blocked by fraud detection engine");
    }

    @Test
    void testFail_WhenNotProcessing_ThrowsTransactionStatusException() {
        Transaction tx = new Transaction();
        tx.setStatus(TransactionStatus.PENDING);

        assertThatThrownBy(() -> tx.fail("some reason"))
                .isInstanceOf(TransactionStatusException.class);
    }

    // ══════════════════════════════════════════════════════════
    //  Helper
    // ══════════════════════════════════════════════════════════

    private Transaction buildApprovedTransaction() {
        Transaction tx = new Transaction();
        tx.setStatus(TransactionStatus.APPROVED);
        return tx;
    }
}
