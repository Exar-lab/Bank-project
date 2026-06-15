package com.banco.co.account.service;

import com.banco.co.account.domain.model.Account;
import com.banco.co.account.domain.port.in.IAccountUseCase;
import com.banco.co.account.domain.port.out.IAccountRepository;
import com.banco.co.account.dto.AccountRequestDto;
import com.banco.co.account.dto.AccountResponseDto;
import com.banco.co.account.dto.AccountUpdateDto;
import com.banco.co.account.enums.AccountStatus;
import com.banco.co.account.exception.account.*;
import com.banco.co.auditLog.enums.AuditAction;
import com.banco.co.auditLog.enums.AuditEntityType;
import com.banco.co.auditLog.model.AuditLogDetail;
import com.banco.co.auditLog.service.IAuditLogService;
import com.banco.co.exception.authentication.UnauthorizedException;
import com.banco.co.outbox.enums.KafkaTopic;
import com.banco.co.outbox.model.OutboxEvent;
import com.banco.co.outbox.port.IOutboxEventPort;
import com.banco.co.user.domain.model.UserSnapshot;
import com.banco.co.user.domain.port.out.IUserRepository;
import com.banco.co.user.model.User;
import com.banco.co.user.service.user.IUserService;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Application service implementing IAccountUseCase (domain input port).
 * All persistence is delegated to the domain IAccountRepository output port.
 * No legacy IAccountService, no legacy Spring Data repo, no MapStruct mapper.
 *
 * Phase 2 rewrite: drops IAccountService, removes legacy IAccountRepository
 * and IAccountMapper, ports toJsonString via ObjectMapper on domain Account fields,
 * uses account.getUserId() for ownership checks.
 */
@Service
@Slf4j
public class AccountService implements IAccountUseCase {

    private final IAccountRepository accountRepository;
    private final IUserRepository userDomainRepository;
    private final IUserService userService;
    private final IAuditLogService auditLogService;
    private final IOutboxEventPort outboxEventPort;
    private final ObjectMapper objectMapper;

    public AccountService(
            IAccountRepository accountRepository,
            IUserRepository userDomainRepository,
            IUserService userService,
            IAuditLogService auditLogService,
            IOutboxEventPort outboxEventPort,
            ObjectMapper objectMapper) {
        this.accountRepository = accountRepository;
        this.userDomainRepository = userDomainRepository;
        this.userService = userService;
        this.auditLogService = auditLogService;
        this.outboxEventPort = outboxEventPort;
        this.objectMapper = objectMapper;
    }

    // ══════════════════════════════════════════════════════════
    //  CREAR CUENTA
    // ══════════════════════════════════════════════════════════

    @Override
    @Transactional
    public AccountResponseDto createAccount(AccountRequestDto dto, String userEmail) {

        UserSnapshot userSnapshot = userDomainRepository.findSnapshotByEmail(userEmail);
        User user = userService.getEntityUserByEmail(userEmail);

        // Validate no duplicate account type for this user
        if (accountRepository.existsByUserEmailAndAccountType(userEmail, dto.accountType())) {

            auditLogService.logFailure(
                    user,
                    AuditAction.ACCOUNT_CREATED_FAILED,
                    AuditEntityType.ACCOUNT,
                    List.of(
                            new AuditLogDetail("message", "Account creation failed: Duplicate types are not allowed"),
                            new AuditLogDetail("userEmail", userEmail),
                            new AuditLogDetail("accountType", dto.accountType())
                    )
            );

            log.error("Account creation failed for user {} due to duplicate account type {}", userEmail, dto.accountType());

            throw new AccountDuplicatedTypeException(userEmail, dto.accountType());
        }

        // Build domain Account from DTO
        Account account = new Account();
        account.setAccountType(dto.accountType());
        account.setCurrency(dto.currency());
        account.setOverdraftLimit(dto.overdraftLimit() != null ? dto.overdraftLimit() : BigDecimal.ZERO);
        account.setInterestRate(dto.interestRate());
        account.setStatus(AccountStatus.ACTIVE);
        account.setUserId(UUID.fromString(userSnapshot.userId()));
        account.generateAccountCode();

        Account savedAccount = accountRepository.save(account);

        String newValues = toJsonString(savedAccount);

        auditLogService.logSuccess(
                user,
                AuditAction.ACCOUNT_CREATED,
                AuditEntityType.ACCOUNT,
                savedAccount.getId().toString(),
                List.of(
                        new AuditLogDetail("message", "Account created successfully"),
                        new AuditLogDetail("accountType", savedAccount.getAccountType()),
                        new AuditLogDetail("accountCode", savedAccount.getAccountCode()),
                        new AuditLogDetail("newValues", newValues)
                )
        );

        outboxEventPort.save(new OutboxEvent(
                "Account",
                savedAccount.getId().toString(),
                "AccountCreated",
                buildPayload(savedAccount, "AccountCreated"),
                KafkaTopic.ACCOUNT_EVENTS
        ));

        log.info("Account {} created for user {} (userId={})", savedAccount.getAccountCode(), userEmail, userSnapshot.userId());

        return toDto(savedAccount, userEmail);
    }

    // ══════════════════════════════════════════════════════════
    //  CONSULTAR CUENTAS
    // ══════════════════════════════════════════════════════════

    @Override
    @Transactional(readOnly = true)
    public AccountResponseDto getAccount(UUID accountId, String userEmail) {

        Account account = accountRepository.findActiveById(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId.toString()));

        User user = userService.getEntityUserByEmail(userEmail);

        validateOwnership(account, user, AuditAction.ACCOUNT_READ);

        log.info("Account {} retrieved by user {}", account.getAccountCode(), userEmail);

        return toDto(account, userEmail);
    }

    @Override
    @Transactional(readOnly = true)
    public AccountResponseDto getAccountByCode(String accountCode, String userEmail) {

        Account account = accountRepository.findActiveByAccountCode(accountCode)
                .orElseThrow(() -> new AccountNotFoundException(accountCode));

        User user = userService.getEntityUserByEmail(userEmail);

        validateOwnership(account, user, AuditAction.ACCOUNT_READ);

        log.info("Account {} retrieved by user {}", accountCode, userEmail);

        return toDto(account, userEmail);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AccountResponseDto> getMyAccounts(String userEmail) {

        log.info("Retrieving all accounts for user {}", userEmail);

        return accountRepository.findActiveAccountsByUserEmail(userEmail).stream()
                .map(account -> toDto(account, userEmail))
                .toList();
    }

    // ══════════════════════════════════════════════════════════
    //  ACTUALIZAR CUENTA
    // ══════════════════════════════════════════════════════════

    @Override
    @Transactional
    public AccountResponseDto updateAccount(String accountCode, AccountUpdateDto dto, String userEmail) {

        Account account = accountRepository.findActiveByAccountCode(accountCode)
                .orElseThrow(() -> new AccountNotFoundException(accountCode));

        User user = userService.getEntityUserByEmail(userEmail);

        validateOwnership(account, user, AuditAction.ACCOUNT_UPDATED);

        String oldValues = toJsonString(account);

        applyUpdate(dto, account);

        Account savedAccount = accountRepository.save(account);

        String newValues = toJsonString(savedAccount);

        auditLogService.logSuccess(
                user,
                AuditAction.ACCOUNT_UPDATED,
                AuditEntityType.ACCOUNT,
                account.getId().toString(),
                List.of(
                        new AuditLogDetail("message", "Account updated successfully"),
                        new AuditLogDetail("accountCode", account.getAccountCode()),
                        new AuditLogDetail("oldValues", oldValues),
                        new AuditLogDetail("newValues", newValues)
                )
        );

        outboxEventPort.save(new OutboxEvent(
                "Account",
                savedAccount.getId().toString(),
                "AccountUpdated",
                buildPayload(savedAccount, "AccountUpdated"),
                KafkaTopic.ACCOUNT_EVENTS
        ));

        log.info("Account {} updated by user {}", accountCode, userEmail);

        return toDto(savedAccount, userEmail);
    }

    // ══════════════════════════════════════════════════════════
    //  CERRAR CUENTA
    // ══════════════════════════════════════════════════════════

    @Override
    @Transactional
    public void closeAccount(UUID accountId, String userEmail) {

        Account account = accountRepository.findActiveByIdWithEnvelopes(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId.toString()));

        User user = userService.getEntityUserByEmail(userEmail);

        validateOwnership(account, user, AuditAction.ACCOUNT_CLOSED);

        // Validate balance == 0
        if (account.getBalance().compareTo(BigDecimal.ZERO) != 0) {

            auditLogService.logFailure(
                    user,
                    AuditAction.ACCOUNT_CLOSED,
                    AuditEntityType.ACCOUNT,
                    List.of(
                            new AuditLogDetail("message", "Cannot close account. Balance must be 0.00"),
                            new AuditLogDetail("accountCode", account.getAccountCode()),
                            new AuditLogDetail("currentBalance", account.getBalance())
                    )
            );

            throw new AccountNotEmptyException(
                    account.getAccountCode(), account.getBalance()
            );
        }

        // Validate no money held in envelopes
        BigDecimal envelopeTotal = account.getMoneyFromEnvelope();

        if (envelopeTotal != null && envelopeTotal.compareTo(BigDecimal.ZERO) > 0) {

            auditLogService.logFailure(
                    user,
                    AuditAction.ACCOUNT_CLOSED,
                    AuditEntityType.ACCOUNT,
                    List.of(
                            new AuditLogDetail("message", "Cannot close account. Envelopes have balance."),
                            new AuditLogDetail("accountCode", account.getAccountCode()),
                            new AuditLogDetail("envelopeTotal", envelopeTotal)
                    )
            );

            throw new AccountHasActiveEnvelopesException(
                    account.getAccountCode(), envelopeTotal
            );
        }

        account.setStatus(AccountStatus.CLOSED);
        accountRepository.save(account);

        auditLogService.logSuccess(
                user,
                AuditAction.ACCOUNT_CLOSED,
                AuditEntityType.ACCOUNT,
                accountId.toString(),
                List.of(
                        new AuditLogDetail("message", "Account closed"),
                        new AuditLogDetail("accountCode", account.getAccountCode())
                )
        );

        outboxEventPort.save(new OutboxEvent(
                "Account",
                accountId.toString(),
                "AccountClosed",
                buildPayload(account, "AccountClosed"),
                KafkaTopic.ACCOUNT_EVENTS
        ));

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
                List.of(
                        new AuditLogDetail("message", "Admin changed account status"),
                        new AuditLogDetail("adminEmail", adminEmail),
                        new AuditLogDetail("accountCode", account.getAccountCode()),
                        new AuditLogDetail("oldStatus", oldStatus),
                        new AuditLogDetail("newStatus", status),
                        new AuditLogDetail("oldValues", oldStatus.toString()),
                        new AuditLogDetail("newValues", status.toString())
                )
        );

        outboxEventPort.save(new OutboxEvent(
                "Account",
                accountId.toString(),
                "AccountStatusChanged",
                buildPayload(savedAccount, "AccountStatusChanged"),
                KafkaTopic.ACCOUNT_EVENTS
        ));

        log.warn("Account {} status changed to {} by admin {}",
                account.getAccountCode(), status, adminEmail);

        return toDto(savedAccount, adminEmail);
    }

    @Override
    @Transactional
    public void closeAccountByAdmin(UUID accountId, String adminEmail) {

        User admin = userService.getEntityUserByEmail(adminEmail);

        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId.toString()));

        // Admin can close even with non-zero balance (special case)
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
                List.of(
                        new AuditLogDetail("message", "Admin closed account"),
                        new AuditLogDetail("adminEmail", adminEmail),
                        new AuditLogDetail("accountCode", account.getAccountCode()),
                        new AuditLogDetail("balance", account.getBalance())
                )
        );

        outboxEventPort.save(new OutboxEvent(
                "Account",
                accountId.toString(),
                "AccountClosedByAdmin",
                buildPayload(account, "AccountClosedByAdmin"),
                KafkaTopic.ACCOUNT_EVENTS
        ));

        log.warn("Account {} closed by admin {}", account.getAccountCode(), adminEmail);
    }

    // ══════════════════════════════════════════════════════════
    //  BALANCE
    // ══════════════════════════════════════════════════════════

    @Override
    @Transactional(readOnly = true)
    public BigDecimal getUnassignedBalance(UUID accountId) {
        Account account = accountRepository.findActiveByIdWithEnvelopes(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId.toString()));

        BigDecimal envelopeTotal = account.getMoneyFromEnvelope() != null
                ? account.getMoneyFromEnvelope()
                : BigDecimal.ZERO;

        return account.getBalance().subtract(envelopeTotal);
    }

    // ══════════════════════════════════════════════════════════
    //  IAccountUseCase — internal domain-typed methods
    // ══════════════════════════════════════════════════════════

    @Override
    @Transactional(readOnly = true)
    public Account getAccountById(UUID accountId) {
        return accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId.toString()));
    }

    @Override
    @Transactional(readOnly = true)
    public Account findAccountWithUserByAccountCode(String accountCode) {
        return accountRepository.findByAccountCodeWithUser(accountCode)
                .orElseThrow(() -> new AccountNotFoundException(accountCode));
    }

    @Override
    @Transactional
    public void updateBalance(Account account) {
        accountRepository.save(account);
    }

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
                    account.getAccountCode(), amount, account.getAvailableBalance()
            );
        }
    }

    // ══════════════════════════════════════════════════════════
    //  PRIVATE HELPERS
    // ══════════════════════════════════════════════════════════

    /**
     * Maps a domain Account to AccountResponseDto.
     * Resolves userEmail from IUserRepository snapshot using account.getUserId().
     */
    private AccountResponseDto toDto(Account account, String fallbackEmail) {
        String userEmail = fallbackEmail;
        if (account.getUserId() != null) {
            try {
                UserSnapshot snapshot = userDomainRepository.findSnapshotByUserId(account.getUserId());
                if (snapshot != null && snapshot.email() != null) {
                    userEmail = snapshot.email();
                }
            } catch (Exception e) {
                log.warn("Could not resolve email for userId={}, using fallback email", account.getUserId());
            }
        }
        return new AccountResponseDto(
                account.getAccountCode(),
                account.getAccountNumber(),
                account.getAccountType(),
                account.getStatus(),
                account.getCurrency(),
                account.getBalance(),
                account.getAvailableBalance(),
                account.getOverdraftLimit(),
                account.getInterestRate(),
                userEmail,
                account.getCreatedAt(),
                account.getUpdatedAt(),
                account.getLastTransactionAt()
        );
    }

    /**
     * Serializes the domain Account to JSON for audit logs.
     * Uses ObjectMapper directly on the domain model — no MapStruct mapper.
     */
    private String toJsonString(Account account) {
        try {
            Map<String, Object> data = new HashMap<>();
            data.put("id", account.getId() != null ? account.getId().toString() : null);
            data.put("accountCode", account.getAccountCode());
            data.put("accountNumber", account.getAccountNumber());
            data.put("accountType", account.getAccountType() != null ? account.getAccountType().name() : null);
            data.put("status", account.getStatus() != null ? account.getStatus().name() : null);
            data.put("currency", account.getCurrency());
            data.put("balance", account.getBalance());
            data.put("overdraftLimit", account.getOverdraftLimit());
            data.put("interestRate", account.getInterestRate());
            data.put("userId", account.getUserId() != null ? account.getUserId().toString() : null);
            return objectMapper.writeValueAsString(data);
        } catch (JacksonException e) {
            return "{\"error\":\"serialization_failed\"}";
        }
    }

    /**
     * Builds the Kafka event payload for account domain events.
     */
    private String buildPayload(Account account, String eventType) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("eventType", eventType);
            payload.put("accountId", account.getId() != null ? account.getId().toString() : null);
            payload.put("accountCode", account.getAccountCode());
            payload.put("accountType", account.getAccountType() != null ? account.getAccountType().name() : null);
            payload.put("status", account.getStatus() != null ? account.getStatus().toString() : null);
            payload.put("balance", account.getBalance());
            return objectMapper.writeValueAsString(payload);
        } catch (JacksonException e) {
            throw new IllegalStateException("Failed to serialize event payload", e);
        }
    }

    /**
     * Validates that the authenticated user owns the given account.
     * Uses account.getUserId() — no lazy User entity traversal.
     */
    private void validateOwnership(Account account, User user, AuditAction auditAction) {

        if (!account.getUserId().equals(user.getId())) {

            auditLogService.logFailure(
                    user,
                    auditAction,
                    AuditEntityType.ACCOUNT,
                    List.of(
                            new AuditLogDetail("message", "Security Violation: User attempted to access Account belonging to other User"),
                            new AuditLogDetail("userId", user.getId()),
                            new AuditLogDetail("userEmail", user.getEmail()),
                            new AuditLogDetail("accountCode", account.getAccountCode()),
                            new AuditLogDetail("ownerId", account.getUserId())
                    )
            );

            throw new UnauthorizedException("You don't own this account");
        }
    }

    /**
     * Applies partial update fields from AccountUpdateDto onto the domain Account.
     * Null fields are ignored (mirrors NullValuePropertyMappingStrategy.IGNORE).
     */
    private void applyUpdate(AccountUpdateDto dto, Account account) {
        if (dto.overdraftLimit() != null) {
            account.setOverdraftLimit(dto.overdraftLimit());
        }
        if (dto.interestRate() != null) {
            account.setInterestRate(dto.interestRate());
        }
        if (dto.currency() != null) {
            account.setCurrency(dto.currency());
        }
    }
}
