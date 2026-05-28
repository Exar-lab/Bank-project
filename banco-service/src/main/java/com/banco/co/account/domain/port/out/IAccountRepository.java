package com.banco.co.account.domain.port.out;

import com.banco.co.account.domain.model.Account;
import com.banco.co.account.enums.AccountType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Output port — persistence contract for the account domain.
 * NO JPA imports, NO Spring Data imports.
 * Implementations live in adapter/out/jpa/.
 */
public interface IAccountRepository {

    Optional<Account> findById(UUID id);

    Optional<Account> findByAccountCode(String code);

    Optional<Account> findByIdWithUser(UUID id);

    List<Account> findAllByUserId(UUID userId);

    Account save(Account account);

    // ══════════════════════════════════════════════════════════
    //  Additional queries required by AccountService
    // ══════════════════════════════════════════════════════════

    Optional<Account> findActiveById(UUID id);

    Optional<Account> findActiveByAccountCode(String code);

    List<Account> findActiveAccountsByUserEmail(String email);

    Optional<Account> findActiveByIdWithEnvelopes(UUID id);

    boolean existsByUserEmailAndAccountType(String email, AccountType accountType);
}
