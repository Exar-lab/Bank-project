package com.banco.co.account.service;

import com.banco.co.account.domain.port.in.IAccountUseCase;
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
import com.banco.co.auditLog.model.AuditLogDetail;
import com.banco.co.auditLog.service.IAuditLogService;
import com.banco.co.envelope.enums.EnvelopeStatus;
import com.banco.co.envelope.model.Envelope;
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
 * Implements both IAccountService (legacy, kept during transition) and
 * IAccountUseCase (domain input port, added in hexagonal migration Phase 2).
 *
 * The legacy IAccountRepository (Spring Data JPA) is kept for the internal
 * methods that return the legacy Account entity (required by IAccountService).
 * IUserRepository (domain port) is injected alongside IUserService so that
 * user snapshots can be retrieved without going through the legacy service.
 *
 * Once the full domain migration is complete, IAccountService and the legacy
 * repository can be removed.
 */
@Service
@Slf4j
public class AccountService implements IAccountService, IAccountUseCase {

    private final IAccountRepository accountRepository;
    /** Domain output port — injected for Phase 1 domain-typed IAccountUseCase methods. */
    private final com.banco.co.account.domain.port.out.IAccountRepository domainAccountRepository;
    private final IUserRepository userDomainRepository;
    private final IUserService userService;
    private final IAuditLogService auditLogService;
    private final IAccountMapper mapper;
    private final IOutboxEventPort outboxEventPort;
    private final ObjectMapper objectMapper;

    public AccountService(
            IAccountRepository accountRepository,
            com.banco.co.account.domain.port.out.IAccountRepository domainAccountRepository,
            IUserRepository userDomainRepository,
            IUserService userService,
            IAuditLogService auditLogService,
            IAccountMapper mapper,
            IOutboxEventPort outboxEventPort,
            ObjectMapper objectMapper) {
        this.accountRepository = accountRepository;
        this.domainAccountRepository = domainAccountRepository;
        this.userDomainRepository = userDomainRepository;
        this.userService = userService;
        this.auditLogService = auditLogService;
        this.mapper = mapper;
        this.outboxEventPort = outboxEventPort;
        this.objectMapper = objectMapper;
    }

    // ══════════════════════════════════════════════════════════
    //  CREAR CUENTA
    // ══════════════════════════════════════════════════════════

    @Override
    @Transactional
    public AccountResponseDto createAccount(AccountRequestDto dto, String userEmail) {

        // Domain port: use UserSnapshot to check identity without loading the full User entity
        UserSnapshot userSnapshot = userDomainRepository.findSnapshotByEmail(userEmail);
        User user = userService.getEntityUserByEmail(userEmail);

        // Validar que no tenga ya una cuenta de este tipo
        if (accountRepository.existsByUser_EmailAndAccountType(userEmail, dto.accountType())) {

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

        // Crear cuenta
        Account account = mapper.toEntity(dto);
        account.setUser(user);
        account.setStatus(AccountStatus.ACTIVE);

        Account savedAccount = accountRepository.save(account);

        String newValues = mapper.toJsonString(savedAccount);

        auditLogService.logSuccess(
                user,
                AuditAction.ACCOUNT_CREATED,
                AuditEntityType.ACCOUNT,
                savedAccount.getId().toString(),
                List.of(
                        new AuditLogDetail("message", "Account created successfully"),
                        new AuditLogDetail("accountType", account.getAccountType()),
                        new AuditLogDetail("accountCode", account.getAccountCode()),
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

        return mapper.toDto(savedAccount);
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

        return mapper.toDto(account);
    }

    @Override
    @Transactional(readOnly = true)
    public AccountResponseDto getAccountByCode(String accountCode, String userEmail) {

        Account account = accountRepository.findActiveAccountByAccountCode(accountCode)
                .orElseThrow(() -> new AccountNotFoundException(accountCode));

        User user = userService.getEntityUserByEmail(userEmail);

        validateOwnership(account, user, AuditAction.ACCOUNT_READ);

        log.info("Account {} retrieved by user {}", accountCode, userEmail);

        return mapper.toDto(account);
    }

    @Override
    @Transactional(readOnly = true)
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

        return mapper.toDto(savedAccount);
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

        // Validar balance = 0
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

        // Validar que no tenga envelopes con balance
        BigDecimal envelopeTotal = getEnvelopeTotal(account);

        if (envelopeTotal.compareTo(BigDecimal.ZERO) > 0) {

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

        // Cerrar
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
                    account.getAccountCode(), amount, account.getAvailableBalance()
            );
        }
    }

    // ── Legacy methods renamed to avoid return-type clash with IAccountUseCase ─────────

    @Override
    @Transactional(readOnly = true)
    @SuppressWarnings("deprecation")
    public Account getAccountEntityById(UUID accountId) {
        return accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId.toString()));
    }

    @Override
    @Transactional(readOnly = true)
    @SuppressWarnings("deprecation")
    public Account findAccountEntityByCode(String accountCode) {
        return accountRepository.findAccountWithUser(accountCode)
                .orElseThrow(() -> new AccountNotFoundException(accountCode));
    }

    @Override
    @Transactional
    public void updateBalance(Account account) {
        accountRepository.save(account);
    }

    // ── IAccountUseCase domain-typed overrides (Phase 1) ──────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public com.banco.co.account.domain.model.Account getAccountById(UUID accountId) {
        return domainAccountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId.toString()));
    }

    @Override
    @Transactional(readOnly = true)
    public com.banco.co.account.domain.model.Account findAccountWithUserByAccountCode(String accountCode) {
        return domainAccountRepository.findByAccountCodeWithUser(accountCode)
                .orElseThrow(() -> new AccountNotFoundException(accountCode));
    }

    @Override
    @Transactional
    public void updateBalance(com.banco.co.account.domain.model.Account account) {
        domainAccountRepository.save(account);
    }

    @Override
    public void validateCanReceiveDeposit(com.banco.co.account.domain.model.Account account) {
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new AccountNotActiveException(
                    account.getAccountCode(), account.getStatus()
            );
        }
    }

    @Override
    public void validateCanWithdraw(com.banco.co.account.domain.model.Account account, BigDecimal amount) {
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

    @Override
    @Transactional(readOnly = true)
    public BigDecimal getUnassignedBalance(UUID accountId) {
        Account account = accountRepository.findActiveByIdWithEnvelopes(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId.toString()));

        BigDecimal envelopeTotal = getEnvelopeTotal(account);

        return account.getBalance().subtract(envelopeTotal);
    }

    // ══════════════════════════════════════════════════════════
    //  MÉTODOS PRIVADOS
    // ══════════════════════════════════════════════════════════

    private String buildPayload(Account account, String eventType) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("eventType", eventType);
            payload.put("accountId", account.getId().toString());
            payload.put("accountCode", account.getAccountCode());
            payload.put("accountType", account.getAccountType());
            payload.put("status", account.getStatus().toString());
            payload.put("balance", account.getBalance());
            return objectMapper.writeValueAsString(payload);
        } catch (JacksonException e) {
            throw new IllegalStateException("Failed to serialize event payload", e);
        }
    }

    private void validateOwnership(Account account, User user, AuditAction auditAction) {

        if (!account.getUser().getId().equals(user.getId())) {

            auditLogService.logFailure(
                    user,
                    auditAction,
                    AuditEntityType.ACCOUNT,
                    List.of(
                            new AuditLogDetail("message", "Security Violation: User attempted to access Account belonging to other User"),
                            new AuditLogDetail("userId", user.getId()),
                            new AuditLogDetail("userEmail", user.getEmail()),
                            new AuditLogDetail("accountCode", account.getAccountCode()),
                            new AuditLogDetail("ownerId", account.getUser().getId()),
                            new AuditLogDetail("ownerEmail", account.getUser().getEmail())
                    )
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
