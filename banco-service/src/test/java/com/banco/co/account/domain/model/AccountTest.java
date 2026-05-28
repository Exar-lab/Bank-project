package com.banco.co.account.domain.model;

import com.banco.co.account.enums.AccountStatus;
import com.banco.co.account.enums.AccountType;
import com.banco.co.account.exception.account.AccountBlockedFundsException;
import com.banco.co.account.exception.account.AccountInsufficientFundsException;
import com.banco.co.account.exception.account.AccountInvalidAmountException;
import com.banco.co.account.exception.account.AccountMaxWithdrawExceededException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pure JUnit 5 — NO Spring context, NO mocks.
 * Tests every business method on the domain Account.
 * Naming: test{Method}_{Condition}_{Expected}
 */
class AccountTest {

    private Account account;

    @BeforeEach
    void setUp() {
        account = new Account();
        account.setId(UUID.randomUUID());
        account.setAccountCode("CR-2024-12345678901234567890");
        account.setAccountNumber("123456789012");
        account.setBalance(new BigDecimal("1000.00"));
        account.setBlockedBalance(BigDecimal.ZERO);
        account.setOverdraftLimit(BigDecimal.ZERO);
        account.setMoneyFromEnvelope(BigDecimal.ZERO);
        account.setAccountType(AccountType.SAVINGS);
        account.setStatus(AccountStatus.ACTIVE);
        account.setCurrency("CRC");
        account.setMaxEnvelope(10);
        account.setUserId(UUID.randomUUID());
    }

    // ══════════════════════════════════════════════════════════
    //  generateAccountCode
    // ══════════════════════════════════════════════════════════

    @Test
    void testGenerateAccountCode_WhenFieldsAreNull_FillsCodeAndNumber() {
        Account fresh = new Account();
        int currentYear = LocalDate.now().getYear();

        fresh.generateAccountCode();

        assertThat(fresh.getAccountCode())
                .matches("CR-" + currentYear + "-\\d{20}");
        assertThat(fresh.getAccountNumber()).matches("\\d{12}");
    }

    @Test
    void testGenerateAccountCode_WhenFieldsAlreadySet_PreservesExistingValues() {
        Account fresh = new Account();
        fresh.generateAccountCode();
        String existingCode = fresh.getAccountCode();
        String existingNumber = fresh.getAccountNumber();

        fresh.generateAccountCode();

        assertThat(fresh.getAccountCode()).isEqualTo(existingCode);
        assertThat(fresh.getAccountNumber()).isEqualTo(existingNumber);
    }

    // ══════════════════════════════════════════════════════════
    //  deposit
    // ══════════════════════════════════════════════════════════

    @Test
    void testDeposit_WhenAmountIsPositive_IncreasesBalance() {
        account.deposit(new BigDecimal("500.00"));

        assertThat(account.getBalance()).isEqualByComparingTo("1500.00");
    }

    @Test
    void testDeposit_WhenAmountIsZero_ThrowsAccountInvalidAmountException() {
        assertThatThrownBy(() -> account.deposit(BigDecimal.ZERO))
                .isInstanceOf(AccountInvalidAmountException.class);
    }

    @Test
    void testDeposit_WhenAmountIsNegative_ThrowsAccountInvalidAmountException() {
        assertThatThrownBy(() -> account.deposit(new BigDecimal("-100.00")))
                .isInstanceOf(AccountInvalidAmountException.class);
    }

    // ══════════════════════════════════════════════════════════
    //  withdraw
    // ══════════════════════════════════════════════════════════

    @Test
    void testWithdraw_WhenAmountIsWithinBalance_DecreasesBalance() {
        account.withdraw(new BigDecimal("300.00"));

        assertThat(account.getBalance()).isEqualByComparingTo("700.00");
    }

    @Test
    void testWithdraw_WhenAmountExceedsBalance_ThrowsAccountMaxWithdrawExceededException() {
        assertThatThrownBy(() -> account.withdraw(new BigDecimal("9999.00")))
                .isInstanceOf(AccountMaxWithdrawExceededException.class);
    }

    @Test
    void testWithdraw_WhenAmountIsZero_ThrowsAccountInvalidAmountException() {
        assertThatThrownBy(() -> account.withdraw(BigDecimal.ZERO))
                .isInstanceOf(AccountInvalidAmountException.class);
    }

    @Test
    void testWithdraw_WhenOverdraftLimitAllows_Succeeds() {
        account.setOverdraftLimit(new BigDecimal("500.00"));

        account.withdraw(new BigDecimal("1400.00"));

        assertThat(account.getBalance()).isEqualByComparingTo("-400.00");
    }

    // ══════════════════════════════════════════════════════════
    //  blockFunds
    // ══════════════════════════════════════════════════════════

    @Test
    void testBlockFunds_WhenSufficientBalance_MovesAmountToBlockedBalance() {
        account.blockFunds(new BigDecimal("200.00"));

        assertThat(account.getBalance()).isEqualByComparingTo("800.00");
        assertThat(account.getBlockedBalance()).isEqualByComparingTo("200.00");
    }

    @Test
    void testBlockFunds_WhenAmountExceedsBalance_ThrowsAccountInsufficientFundsException() {
        assertThatThrownBy(() -> account.blockFunds(new BigDecimal("5000.00")))
                .isInstanceOf(AccountInsufficientFundsException.class);
    }

    // ══════════════════════════════════════════════════════════
    //  unblockFunds
    // ══════════════════════════════════════════════════════════

    @Test
    void testUnblockFunds_WhenBlockedBalanceSufficient_RestoresToBalance() {
        account.blockFunds(new BigDecimal("200.00"));

        account.unblockFunds(new BigDecimal("200.00"));

        assertThat(account.getBalance()).isEqualByComparingTo("1000.00");
        assertThat(account.getBlockedBalance()).isEqualByComparingTo("0.00");
    }

    @Test
    void testUnblockFunds_WhenMoreThanBlocked_ThrowsAccountBlockedFundsException() {
        account.blockFunds(new BigDecimal("100.00"));

        assertThatThrownBy(() -> account.unblockFunds(new BigDecimal("500.00")))
                .isInstanceOf(AccountBlockedFundsException.class);
    }

    // ══════════════════════════════════════════════════════════
    //  confirmBlockedFunds
    // ══════════════════════════════════════════════════════════

    @Test
    void testConfirmBlockedFunds_WhenBlockedBalanceSufficient_RemovesFromBlocked() {
        account.blockFunds(new BigDecimal("200.00"));

        account.confirmBlockedFunds(new BigDecimal("200.00"));

        assertThat(account.getBlockedBalance()).isEqualByComparingTo("0.00");
        assertThat(account.getBalance()).isEqualByComparingTo("800.00");
    }

    @Test
    void testConfirmBlockedFunds_WhenMoreThanBlocked_ThrowsAccountBlockedFundsException() {
        account.blockFunds(new BigDecimal("100.00"));

        assertThatThrownBy(() -> account.confirmBlockedFunds(new BigDecimal("500.00")))
                .isInstanceOf(AccountBlockedFundsException.class);
    }

    // ══════════════════════════════════════════════════════════
    //  depositFromEnvelope / withdrawFromEnvelope
    // ══════════════════════════════════════════════════════════

    @Test
    void testDepositFromEnvelope_WhenCalled_IncreasesBalanceAndDecreasesMoneyFromEnvelope() {
        account.setMoneyFromEnvelope(new BigDecimal("300.00"));

        account.depositFromEnvelope(new BigDecimal("100.00"));

        assertThat(account.getBalance()).isEqualByComparingTo("1100.00");
        assertThat(account.getMoneyFromEnvelope()).isEqualByComparingTo("200.00");
    }

    @Test
    void testWithdrawFromEnvelope_WhenCalled_DecreasesBalanceAndIncreasesMoneyFromEnvelope() {
        account.withdrawFromEnvelope(new BigDecimal("200.00"));

        assertThat(account.getBalance()).isEqualByComparingTo("800.00");
        assertThat(account.getMoneyFromEnvelope()).isEqualByComparingTo("200.00");
    }

    // ══════════════════════════════════════════════════════════
    //  getTotalBalance / getAvailableBalance
    // ══════════════════════════════════════════════════════════

    @Test
    void testGetTotalBalance_ReturnsSumOfBalanceAndBlockedBalance() {
        account.blockFunds(new BigDecimal("300.00"));

        BigDecimal total = account.getTotalBalance();

        assertThat(total).isEqualByComparingTo("1000.00");
    }

    @Test
    void testGetAvailableBalance_ReturnsCurrentBalance() {
        assertThat(account.getAvailableBalance()).isEqualByComparingTo("1000.00");
    }

    // ══════════════════════════════════════════════════════════
    //  addEnvelope
    // ══════════════════════════════════════════════════════════

    @Test
    void testAddEnvelope_WhenCalled_AddsEnvelopeIdToList() {
        UUID envelopeId = UUID.randomUUID();

        account.addEnvelopeId(envelopeId);

        assertThat(account.getEnvelopeIds()).contains(envelopeId);
        assertThat(account.getEnvelopeIds()).hasSize(1);
    }

    @Test
    void testAddEnvelope_WhenCalledMultipleTimes_AllEnvelopesAdded() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();

        account.addEnvelopeId(id1);
        account.addEnvelopeId(id2);

        assertThat(account.getEnvelopeIds()).containsExactlyInAnyOrder(id1, id2);
    }
}
