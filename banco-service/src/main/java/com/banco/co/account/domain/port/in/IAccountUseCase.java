package com.banco.co.account.domain.port.in;

import com.banco.co.account.domain.model.Account;
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

    // ══════════════════════════════════════════════════════════
    //  INTERNAL DOMAIN-TYPED METHODS (for cross-feature callers)
    //  All use com.banco.co.account.domain.model.Account.
    //  Default stubs are here to keep AccountService compilable
    //  during the Phase 1 → Phase 2 transition.
    //  Phase 2 (Task 2.1) rewrites AccountService to override these.
    // ══════════════════════════════════════════════════════════

    /**
     * Lookup account by UUID — no ownership check (internal use).
     * Throws AccountNotFoundException if not found.
     */
    default Account getAccountById(UUID accountId) {
        throw new UnsupportedOperationException(
                "getAccountById not yet implemented — will be overridden in Phase 2 AccountService rewrite");
    }

    /**
     * Lookup account by account code with user context (userId populated).
     * No ownership check — caller is responsible for authorization.
     * Throws AccountNotFoundException if not found.
     */
    default Account findAccountWithUserByAccountCode(String accountCode) {
        throw new UnsupportedOperationException(
                "findAccountWithUserByAccountCode not yet implemented — will be overridden in Phase 2 AccountService rewrite");
    }

    /**
     * Persist balance changes after fund mutations.
     */
    default void updateBalance(Account account) {
        throw new UnsupportedOperationException(
                "updateBalance not yet implemented — will be overridden in Phase 2 AccountService rewrite");
    }

    /**
     * Assert account status == ACTIVE; throws AccountNotActiveException otherwise.
     */
    default void validateCanReceiveDeposit(Account account) {
        throw new UnsupportedOperationException(
                "validateCanReceiveDeposit not yet implemented — will be overridden in Phase 2 AccountService rewrite");
    }

    /**
     * Assert status == ACTIVE and balance >= amount; throws on failure.
     */
    default void validateCanWithdraw(Account account, BigDecimal amount) {
        throw new UnsupportedOperationException(
                "validateCanWithdraw not yet implemented — will be overridden in Phase 2 AccountService rewrite");
    }
}
