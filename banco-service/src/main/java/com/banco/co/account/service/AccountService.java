package com.banco.co.account.service;

import com.banco.co.account.dto.AccountRequestDto;
import com.banco.co.account.dto.AccountResponseDto;
import com.banco.co.account.dto.AccountUpdateDto;
import com.banco.co.account.enums.AccountStatus;
import com.banco.co.account.exception.account.*;
import com.banco.co.account.mapper.IAccountMapper;
import com.banco.co.account.model.Account;
import com.banco.co.account.repository.IAccountRepository;
import com.banco.co.auditLog.enums.AuditAction;
import com.banco.co.auditLog.enums.AuditEntityType;
import com.banco.co.auditLog.service.IAuditLogService;
import com.banco.co.envelope.enums.EnvelopeStatus;
import com.banco.co.envelope.model.Envelope;
import com.banco.co.exception.authentication.UnauthorizedException;
import com.banco.co.user.model.User;
import com.banco.co.user.service.user.IUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccountService implements IAccountService {
    private final IAccountRepository accountRepository;
    private final IUserService userService;
    private final IAuditLogService auditLogService;
    private final IAccountMapper mapper;

    // ══════════════════════════════════════════════════════════
    //  CREAR CUENTA
    // ══════════════════════════════════════════════════════════

    @Override
    @Transactional
    public AccountResponseDto createAccount(AccountRequestDto dto, String userEmail) {

        User user = userService.getEntityUserByEmail(userEmail);

        // Validar que no tenga ya una cuenta de este tipo
        if (accountRepository.existsByUser_EmailAndAccountType(userEmail, dto.accountType())) {

            String details = String.format(
                    "Account creation failed: User %s already has an active %s account. " +
                            "Duplicate types are not allowed.",
                    userEmail, dto.accountType()
            );

            auditLogService.logFailure(
                    user,
                    AuditAction.ACCOUNT_CREATED_FAILED,
                    AuditEntityType.ACCOUNT,
                    details
            );

            log.error("Account creation failed: {}", details);

            throw new AccountDuplicatedTypeException(userEmail, dto.accountType());
        }

        // Crear cuenta
        Account account = mapper.toEntity(dto);
        account.setUser(user);
        account.setStatus(AccountStatus.ACTIVE);
        account.setBalance(BigDecimal.ZERO);

        Account savedAccount = accountRepository.save(account);

        String details = String.format(
                "Account created successfully. Type: %s, Code: %s",
                account.getAccountType(), account.getAccountCode()
        );

        String newValues = mapper.toJsonString(savedAccount);

        auditLogService.logSuccess(
                user,
                AuditAction.ACCOUNT_CREATED,
                AuditEntityType.ACCOUNT,
                savedAccount.getId().toString(),
                details,
                null,
                newValues
        );

        log.info("Account {} created for user {}", savedAccount.getAccountCode(), userEmail);

        return mapper.toDto(savedAccount);
    }

    // ══════════════════════════════════════════════════════════
    //  CONSULTAR CUENTAS
    // ══════════════════════════════════════════════════════════

    @Override
    public AccountResponseDto getAccount(UUID accountId, String userEmail) {

        Account account = accountRepository.findActiveById(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId.toString()));

        User user = userService.getEntityUserByEmail(userEmail);

        validateOwnership(account, user, AuditAction.ACCOUNT_READ);

        log.info("Account {} retrieved by user {}", account.getAccountCode(), userEmail);

        return mapper.toDto(account);
    }

    @Override
    public AccountResponseDto getAccountByCode(String accountCode, String userEmail) {

        Account account = accountRepository.findActiveAccountByAccountCode(accountCode)
                .orElseThrow(() -> new AccountNotFoundException(accountCode));

        User user = userService.getEntityUserByEmail(userEmail);

        validateOwnership(account, user, AuditAction.ACCOUNT_READ);

        log.info("Account {} retrieved by user {}", accountCode, userEmail);

        return mapper.toDto(account);
    }

    @Override
    public List<AccountResponseDto> getMyAccounts(String userEmail) {

        log.info("Retrieving all accounts for user {}", userEmail);

        return accountRepository.findActiveAccountsByUser_Email(userEmail).stream()
                .map(mapper::toDto)
                .toList();
    }

    // ══════════════════════════════════════════════════════════
    //  ACTUALIZAR CUENTA
    // ══════════════════════════════════════════════════════════

    @Override
    @Transactional
    public AccountResponseDto updateAccount(String accountCode, AccountUpdateDto dto, String userEmail) {

        Account account = accountRepository.findActiveAccountByAccountCode(accountCode)
                .orElseThrow(() -> new AccountNotFoundException(accountCode));

        User user = userService.getEntityUserByEmail(userEmail);

        validateOwnership(account, user, AuditAction.ACCOUNT_UPDATED);

        String oldValues = mapper.toJsonString(account);

        mapper.updateEntityFromDto(dto, account);

        Account savedAccount = accountRepository.save(account);

        String newValues = mapper.toJsonString(savedAccount);

        String details = String.format(
                "Account %s updated successfully",
                account.getAccountCode()
        );

        auditLogService.logSuccess(
                user,
                AuditAction.ACCOUNT_UPDATED,
                AuditEntityType.ACCOUNT,
                account.getId().toString(),
                details,
                oldValues,
                newValues
        );

        log.info("Account {} updated by user {}", accountCode, userEmail);

        return mapper.toDto(savedAccount);
    }

    // ══════════════════════════════════════════════════════════
    //  CERRAR CUENTA
    // ══════════════════════════════════════════════════════════

    @Override
    @Transactional
    public void closeAccount(UUID accountId, String userEmail) {

        Account account = accountRepository.findActiveById(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId.toString()));

        User user = userService.getEntityUserByEmail(userEmail);

        validateOwnership(account, user, AuditAction.ACCOUNT_CLOSED);

        // Validar balance = 0
        if (account.getBalance().compareTo(BigDecimal.ZERO) != 0) {

            String details = String.format(
                    "Cannot close account %s. Balance must be 0.00. Current: %s",
                    account.getAccountCode(), account.getBalance()
            );

            auditLogService.logFailure(
                    user,
                    AuditAction.ACCOUNT_CLOSED,
                    AuditEntityType.ACCOUNT,
                    details
            );

            throw new AccountNotEmptyException(
                    account.getAccountCode(), account.getBalance()
            );
        }

        // Validar que no tenga envelopes con balance
        BigDecimal envelopeTotal = getEnvelopeTotal(account);

        if (envelopeTotal.compareTo(BigDecimal.ZERO) > 0) {

            String details = String.format(
                    "Cannot close account %s. Envelopes have balance: %s",
                    account.getAccountCode(), envelopeTotal
            );

            auditLogService.logFailure(
                    user,
                    AuditAction.ACCOUNT_CLOSED,
                    AuditEntityType.ACCOUNT,
                    details
            );

            throw new AccountHasActiveEnvelopesException(
                    account.getAccountCode(), envelopeTotal
            );
        }

        // Cerrar
        account.setStatus(AccountStatus.CLOSED);
        accountRepository.save(account);

        auditLogService.logSuccess(
                user,
                AuditAction.ACCOUNT_CLOSED,
                AuditEntityType.ACCOUNT,
                accountId.toString(),
                String.format("Account %s closed", account.getAccountCode()),
                null,
                null
        );

        log.info("Account {} closed by user {}", account.getAccountCode(), userEmail);
    }

    // ══════════════════════════════════════════════════════════
    //  OPERACIONES ADMINISTRATIVAS
    // ══════════════════════════════════════════════════════════

    @Override
    @Transactional
    public AccountResponseDto updateAccountStatus(
            UUID accountId,
            AccountStatus status,
            String adminEmail
    ) {
        User admin = userService.getEntityUserByEmail(adminEmail);

        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId.toString()));

        AccountStatus oldStatus = account.getStatus();
        account.setStatus(status);

        Account savedAccount = accountRepository.save(account);

        auditLogService.logSuccess(
                admin,
                AuditAction.ACCOUNT_STATUS_CHANGED,
                AuditEntityType.ACCOUNT,
                accountId.toString(),
                String.format("Admin %s changed account %s status from %s to %s",
                        adminEmail, account.getAccountCode(), oldStatus, status),
                oldStatus.toString(),
                status.toString()
        );

        log.warn("Account {} status changed to {} by admin {}",
                account.getAccountCode(), status, adminEmail);

        return mapper.toDto(savedAccount);
    }

    @Override
    @Transactional
    public void closeAccountByAdmin(UUID accountId, String adminEmail) {

        User admin = userService.getEntityUserByEmail(adminEmail);

        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId.toString()));

        // Admin puede cerrar aunque tenga balance (caso especial)
        // Pero debe auditar si tiene balance
        if (account.getBalance().compareTo(BigDecimal.ZERO) > 0) {
            log.warn("Admin {} closing account {} with non-zero balance: {}",
                    adminEmail, account.getAccountCode(), account.getBalance());
        }

        account.setStatus(AccountStatus.CLOSED);
        accountRepository.save(account);

        auditLogService.logSuccess(
                admin,
                AuditAction.ACCOUNT_CLOSED_BY_ADMIN,
                AuditEntityType.ACCOUNT,
                accountId.toString(),
                String.format("Admin %s closed account %s (Balance: %s)",
                        adminEmail, account.getAccountCode(), account.getBalance()),
                null,
                null
        );

        log.warn("Account {} closed by admin {}", account.getAccountCode(), adminEmail);
    }

    // ══════════════════════════════════════════════════════════
    //  MÉTODOS AUXILIARES (Para TransactionService, EnvelopeService)
    // ══════════════════════════════════════════════════════════

    @Override
    public void validateCanReceiveDeposit(Account account) {
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new AccountNotActiveException(
                    account.getAccountCode(), account.getStatus()
            );
        }
    }

    @Override
    public void validateCanWithdraw(Account account, BigDecimal amount) {

        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new AccountNotActiveException(
                    account.getAccountCode(), account.getStatus()
            );
        }

        if (account.getBalance().compareTo(amount) < 0) {
            throw new AccountInsufficientFundsException(
                    account.getAccountCode(),amount,account.getAvailableBalance()
            );
        }
    }

    @Override
    public Account getAccountById(UUID accountId) {
        return accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId.toString()));
    }

    @Override
    public Account findAccountWithUserByAccountCode(String accountCode) {
        return accountRepository.findAccountWithUser(accountCode)
                .orElseThrow(() -> new AccountNotFoundException(accountCode));
    }

    @Override
    @Transactional
    public void updateBalance(Account account) {
        accountRepository.save(account);
    }

    @Override
    public BigDecimal getUnassignedBalance(UUID accountId) {
        Account account = getAccountById(accountId);

        BigDecimal envelopeTotal = getEnvelopeTotal(account);

        return account.getBalance().subtract(envelopeTotal);
    }

    // ══════════════════════════════════════════════════════════
    //  MÉTODOS PRIVADOS
    // ══════════════════════════════════════════════════════════

    private void validateOwnership(Account account, User user, AuditAction auditAction) {

        if (!account.getUser().getId().equals(user.getId())) {

            String details = String.format(
                    "Security Violation: User [ID: %s, Email: %s] attempted to access Account [Code: %s] " +
                            "belonging to User [ID: %s, Email: %s]",
                    user.getId(), user.getEmail(),
                    account.getAccountCode(),
                    account.getUser().getId(), account.getUser().getEmail()
            );

            auditLogService.logFailure(
                    user,
                    auditAction,
                    AuditEntityType.ACCOUNT,
                    details
            );

            throw new UnauthorizedException("You don't own this account");
        }
    }

    private BigDecimal getEnvelopeTotal(Account account) {
        return account.getEnvelopes().stream()
                .filter(e -> e.getStatus() == EnvelopeStatus.ACTIVE)
                .map(Envelope::getBalance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
