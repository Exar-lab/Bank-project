package com.banco.co.notification.email.dto;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for transaction email context DTOs.
 * TS-015 and TS-016.
 */
class TransactionEmailContextTest {

    // ══════════════════════════════════════════════════════════
    //  TS-015 — TransferSenderEmailContext all fields round-trip
    // ══════════════════════════════════════════════════════════

    @Test
    void testTransferSenderEmailContext_AllFieldsAccessible() {
        BigDecimal amount = new BigDecimal("100000");
        LocalDateTime occurredAt = LocalDateTime.of(2026, 4, 22, 14, 35, 12);

        TransferSenderEmailContext ctx = new TransferSenderEmailContext(
                "Ana", amount, "CRC", "Pedro", "ACC-TO-001",
                "ACC-FROM-001", "TXN-BCR-001", occurredAt, "Banco CO"
        );

        assertThat(ctx.recipientName()).isEqualTo("Ana");
        assertThat(ctx.amount()).isEqualByComparingTo(amount);
        assertThat(ctx.currency()).isEqualTo("CRC");
        assertThat(ctx.counterpartyName()).isEqualTo("Pedro");
        assertThat(ctx.counterpartyAccountCode()).isEqualTo("ACC-TO-001");
        assertThat(ctx.fromAccountCode()).isEqualTo("ACC-FROM-001");
        assertThat(ctx.transactionCode()).isEqualTo("TXN-BCR-001");
        assertThat(ctx.occurredAt()).isEqualTo(occurredAt);
        assertThat(ctx.bankName()).isEqualTo("Banco CO");
    }

    @Test
    void testTransferReceiverEmailContext_AllFieldsAccessible() {
        BigDecimal amount = new BigDecimal("100000");
        LocalDateTime occurredAt = LocalDateTime.of(2026, 4, 22, 14, 35, 12);

        TransferReceiverEmailContext ctx = new TransferReceiverEmailContext(
                "Pedro", amount, "CRC", "Ana", "ACC-FROM-001",
                "ACC-TO-001", "TXN-BCR-001", occurredAt, "Banco CO"
        );

        assertThat(ctx.recipientName()).isEqualTo("Pedro");
        assertThat(ctx.amount()).isEqualByComparingTo(amount);
        assertThat(ctx.counterpartyName()).isEqualTo("Ana");
        assertThat(ctx.counterpartyAccountCode()).isEqualTo("ACC-FROM-001");
        assertThat(ctx.toAccountCode()).isEqualTo("ACC-TO-001");
        assertThat(ctx.bankName()).isEqualTo("Banco CO");
    }

    // ══════════════════════════════════════════════════════════
    //  TS-016 — DEPOSIT context has non-blank operationLabel
    // ══════════════════════════════════════════════════════════

    @Test
    void testAccountOperationEmailContext_DepositHasNonBlankOperationLabel() {
        AccountOperationEmailContext ctx = new AccountOperationEmailContext(
                "Pedro", "DEPOSIT", "Depósito", new BigDecimal("50000"), "CRC",
                "ACC-TO-001", "TXN-DEP-001", LocalDateTime.now(), "Banco CO"
        );

        assertThat(ctx.operationLabel()).isNotBlank();
        assertThat(ctx.operationType()).isEqualTo("DEPOSIT");
        assertThat(ctx.recipientName()).isEqualTo("Pedro");
        assertThat(ctx.accountCode()).isEqualTo("ACC-TO-001");
    }

    @Test
    void testAccountOperationEmailContext_WithdrawalHasNonBlankOperationLabel() {
        AccountOperationEmailContext ctx = new AccountOperationEmailContext(
                "Ana", "WITHDRAWAL", "Retiro", new BigDecimal("20000"), "CRC",
                "ACC-FROM-001", "TXN-WD-001", LocalDateTime.now(), "Banco CO"
        );

        assertThat(ctx.operationLabel()).isNotBlank();
        assertThat(ctx.operationType()).isEqualTo("WITHDRAWAL");
    }
}
