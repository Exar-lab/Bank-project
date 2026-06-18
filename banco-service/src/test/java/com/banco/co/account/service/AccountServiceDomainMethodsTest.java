package com.banco.co.account.service;

import com.banco.co.account.domain.model.Account;
import com.banco.co.account.domain.port.in.IAccountUseCase;
import com.banco.co.account.exception.account.AccountNotFoundException;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Task 1.3 RED tests — verifies that IAccountUseCase exposes the 5 domain-typed methods.
 * This file will fail to compile until the 5 method signatures are added to IAccountUseCase.
 *
 * These are compilation-gate tests; runtime assertions are minimal.
 * Full behavioral tests live in AccountServiceTest (Phase 2, Task 5.4).
 */
class AccountServiceDomainMethodsTest {

    /**
     * RED: getAccountById does not exist on IAccountUseCase yet — compilation fails.
     * GREEN: after the 5 methods are added, the mock call compiles.
     */
    @Test
    void testGetAccountById_NotFound_ThrowsAccountNotFoundException() {
        IAccountUseCase useCase = mock(IAccountUseCase.class);
        UUID unknownId = UUID.randomUUID();

        when(useCase.getAccountById(unknownId))
                .thenThrow(new AccountNotFoundException(unknownId.toString()));

        assertThatThrownBy(() -> useCase.getAccountById(unknownId))
                .isInstanceOf(AccountNotFoundException.class);
    }

    @Test
    void testFindAccountWithUserByAccountCode_NotFound_ThrowsAccountNotFoundException() {
        IAccountUseCase useCase = mock(IAccountUseCase.class);
        String unknownCode = "NONEXISTENT";

        when(useCase.findAccountWithUserByAccountCode(unknownCode))
                .thenThrow(new AccountNotFoundException(unknownCode));

        assertThatThrownBy(() -> useCase.findAccountWithUserByAccountCode(unknownCode))
                .isInstanceOf(AccountNotFoundException.class);
    }

    @Test
    void testUpdateBalance_CallsPort() {
        IAccountUseCase useCase = mock(IAccountUseCase.class);
        Account account = new Account();

        // RED: updateBalance not on IAccountUseCase yet
        useCase.updateBalance(account);

        // Verifying no exception thrown — the mock handles it by default
    }

    @Test
    void testValidateCanReceiveDeposit_DoesNotThrowForMockedCase() {
        IAccountUseCase useCase = mock(IAccountUseCase.class);
        Account account = new Account();

        // RED: validateCanReceiveDeposit not on IAccountUseCase yet
        useCase.validateCanReceiveDeposit(account);
    }

    @Test
    void testValidateCanWithdraw_DoesNotThrowForMockedCase() {
        IAccountUseCase useCase = mock(IAccountUseCase.class);
        Account account = new Account();

        // RED: validateCanWithdraw not on IAccountUseCase yet
        useCase.validateCanWithdraw(account, java.math.BigDecimal.TEN);
    }
}
