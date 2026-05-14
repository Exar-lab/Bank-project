package com.banco.co.account.domain.port.in;

import com.banco.co.account.dto.AccountRequestDto;
import com.banco.co.account.dto.AccountResponseDto;
import com.banco.co.account.dto.AccountUpdateDto;
import com.banco.co.account.enums.AccountStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Input port — use cases exposed by the account domain.
 * NO JPA imports, NO Spring Data imports.
 * Implementations live in the application layer (AccountService).
 *
 * NOTE: Internal helper methods that currently return legacy Account entities
 * (getAccountById, findAccountWithUserByAccountCode, updateBalance,
 * validateCanReceiveDeposit, validateCanWithdraw) are intentionally excluded here
 * to avoid conflicting return types with IAccountService during the additive
 * migration phase. They will be added after the full domain model migration.
 */
public interface IAccountUseCase {

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
    AccountResponseDto updateAccountStatus(UUID accountId, AccountStatus status, String adminEmail);

    /**
     * Admin cierra cuenta de usuario
     */
    void closeAccountByAdmin(UUID accountId, String adminEmail);

    // ══════════════════════════════════════════════════════════
    //  BALANCE
    // ══════════════════════════════════════════════════════════

    /**
     * Muestra el dinero disponible en la cuenta (sin contar el dinero de sobres)
     */
    BigDecimal getUnassignedBalance(UUID accountId);
}
