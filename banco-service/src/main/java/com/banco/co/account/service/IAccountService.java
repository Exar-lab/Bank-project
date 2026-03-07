package com.banco.co.account.service;

import com.banco.co.account.dto.AccountRequestDto;
import com.banco.co.account.dto.AccountResponseDto;
import com.banco.co.account.dto.AccountUpdateDto;
import com.banco.co.account.enums.AccountStatus;
import com.banco.co.account.model.Account;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IAccountService {
    // ══════════════════════════════════════════════════════════
    //  CRUD DE CUENTAS
    // ══════════════════════════════════════════════════════════

    /**
     * Crear cuenta para el usuario autenticado
     */
    AccountResponseDto createAccount(AccountRequestDto dto, String userEmail);

    /**
     * Obtener cuenta específica del usuario
     */
    AccountResponseDto getAccount(UUID accountId, String userEmail);

    /**
     * Obtener cuenta por código
     */
    AccountResponseDto getAccountByCode(String accountCode, String userEmail);

    /**
     * Obtener todas las cuentas del usuario
     */
    List<AccountResponseDto> getMyAccounts(String userEmail);

    /**
     * Actualizar datos de cuenta
     */
    AccountResponseDto updateAccount(String accountCode, AccountUpdateDto dto, String userEmail);

    /**
     * Cerrar cuenta (soft delete)
     * Solo si balance = 0
     */
    void closeAccount(UUID accountId, String userEmail);

    // ══════════════════════════════════════════════════════════
    //  OPERACIONES ADMINISTRATIVAS
    // ══════════════════════════════════════════════════════════

    /**
     * Admin cambia status de cuenta
     */
    AccountResponseDto updateAccountStatus(
            UUID accountId,
            AccountStatus status,
            String adminEmail
    );

    /**
     * Admin cierra cuenta de usuario
     */
    void closeAccountByAdmin(UUID accountId, String adminEmail);

    // ══════════════════════════════════════════════════════════
    //  MÉTODOS DE VALIDACIÓN (Para uso interno de TransactionService)
    // ══════════════════════════════════════════════════════════

    /**
     * Validar que una cuenta puede recibir depósitos
     */
    void validateCanReceiveDeposit(Account account);

    /**
     * Validar que una cuenta puede hacer retiros
     */
    void validateCanWithdraw(Account account, BigDecimal amount);

    /**
     * Obtener cuenta por ID (sin validar ownership)
     * USO INTERNO: Para TransactionService
     */
    Account getAccountById(UUID accountId);

    /**
     * Obtener cuenta por código (sin validar ownership)
     * USO INTERNO: Para TransactionService
     */
    Account findAccountWithUserByAccountCode(String accountCode);

    /**
     * Actualizar balance de cuenta
     * USO INTERNO: Solo TransactionService debería llamar esto
     */
    void updateBalance(Account account);

    /**
     * Muestra el dinero disponible en la cuenta (sin contar el dinero de sobres)
     */
    BigDecimal getUnassignedBalance(UUID accountId);

}
