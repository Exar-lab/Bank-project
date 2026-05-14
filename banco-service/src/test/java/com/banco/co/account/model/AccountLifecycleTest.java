package com.banco.co.account.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class AccountLifecycleTest {

    @Test
    void testGenerateAccountCode_WhenGeneratedFieldsAreNull_FillsAccountCodeAndAccountNumber() {
        int currentYear = LocalDate.now().getYear();
        Account account = new Account();

        account.generateAccountCode();

        assertThat(account.getAccountCode())
                .matches("CR-" + currentYear + "-\\d{20}");
        assertThat(account.getAccountNumber()).matches("\\d{12}");
    }

    @Test
    void testGenerateAccountCode_WhenGeneratedFieldsAlreadyExist_PreservesExistingValues() {
        Account account = new Account();

        account.generateAccountCode();
        String generatedAccountCode = account.getAccountCode();
        String generatedAccountNumber = account.getAccountNumber();

        account.generateAccountCode();

        assertThat(account.getAccountCode()).isEqualTo(generatedAccountCode);
        assertThat(account.getAccountNumber()).isEqualTo(generatedAccountNumber);
    }
}
