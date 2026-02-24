package com.banco.co.account.service;

import com.banco.co.account.dto.AccountRequestDto;
import com.banco.co.account.dto.AccountResponseDto;
import com.banco.co.account.dto.AccountUpdateDto;
import com.banco.co.account.enums.AccountStatus;

import java.math.BigDecimal;
import java.util.UUID;

public interface IAccountService {
    AccountResponseDto createAccount(AccountRequestDto accountRequestDto);
    AccountResponseDto updateAccount(AccountUpdateDto accountRequestDto);
    void deleteAccount(String userEmail,String adminEmail);
    AccountResponseDto getAccount(String userEmail);
    AccountResponseDto getAccountByCode(String accountCode);
    AccountResponseDto updateAccountStatus(UUID userId, AccountStatus accountStatus, String adminEmail);

    boolean validateAmount(String accountNumber, BigDecimal amount);

    AccountResponseDto deposit(String accountCode, BigDecimal amount);
    AccountResponseDto withdraw(String accountCode, BigDecimal amount);
    AccountResponseDto transfer(String fromAccountCode, String toAccountCode, BigDecimal amount);
    AccountResponseDto transferToEnvelope(String accountCode, BigDecimal amount);

}
