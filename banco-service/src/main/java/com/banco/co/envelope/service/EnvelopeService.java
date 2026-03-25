package com.banco.co.envelope.service;

import com.banco.co.account.model.Account;
import com.banco.co.account.service.IAccountService;
import com.banco.co.auditLog.enums.AuditAction;
import com.banco.co.auditLog.enums.AuditEntityType;
import com.banco.co.auditLog.model.AuditLogDetail;
import com.banco.co.auditLog.service.IAuditLogService;
import com.banco.co.envelope.dto.*;
import com.banco.co.envelope.enums.EnvelopeStatus;
import com.banco.co.envelope.enums.EnvelopeType;
import com.banco.co.envelope.exception.*;
import com.banco.co.envelope.mapper.IEnvelopeMapper;
import com.banco.co.envelope.model.Envelope;
import com.banco.co.envelope.repository.IEnvelopeRepository;
import com.banco.co.exception.authentication.UnauthorizedException;
import com.banco.co.outbox.enums.KafkaTopic;
import com.banco.co.outbox.model.OutboxEvent;
import com.banco.co.outbox.port.IOutboxEventPort;
import com.banco.co.user.model.User;
import com.banco.co.user.service.user.IUserService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
@RequiredArgsConstructor
@Service
@Slf4j
public class EnvelopeService implements IEnvelopeService {
    private final IEnvelopeRepository repository;
    private final IAccountService accountService;
    private final IUserService userService;
    private final IEnvelopeMapper mapper;
    private final IAuditLogService auditLogService;
    private final IOutboxEventPort outboxEventRepository;
    private final ObjectMapper objectMapper;

    // ══════════════════════════════════════════════════════════
    //  CREAR ENVELOPE
    // ══════════════════════════════════════════════════════════

    @Override
    @Transactional
    public EnvelopeResponseDto create(EnvelopeRequestDto dto, String userEmail) {

        User user = userService.getEntityUserByEmail(userEmail);
        Account account = accountService.findAccountWithUserByAccountCode(dto.accountCode());

        // Validar ownership
        if (!account.getUser().getId().equals(user.getId())) {
            throw new UnauthorizedException("You don't own this account");
        }

        // Validar límite de envelopes por cuenta
        long envelopeCount = repository.countByAccount_IdAndStatus(
                account.getId(), EnvelopeStatus.ACTIVE
        );

        if (envelopeCount >= account.getMaxEnvelope()) {
            throw new MaxEnvelopesExceededException(
                    account.getAccountCode()
            );
        }

        Envelope envelope = mapper.toEntity(dto);
        envelope.setAccount(account);

        // Si tiene auto-contribute, calcular próxima fecha
        if (envelope.getAutoContribute()) {
            envelope.setNextContributionDate(
                    LocalDate.now().plusDays(envelope.getAutoContributeFrequency().getDays())
            );
        }

        Envelope savedEnvelope = repository.save(envelope);

        outboxEventRepository.save(new OutboxEvent(
                "Envelope",
                savedEnvelope.getId().toString(),
                "EnvelopeCreated",
                buildEnvelopePayload(savedEnvelope),
                KafkaTopic.ENVELOPE_EVENTS
        ));

        auditLogService.logSuccess(
                user,
                AuditAction.ENVELOPE_CREATED,
                AuditEntityType.ENVELOPE,
                savedEnvelope.getId().toString(),
                List.of(
                        new AuditLogDetail("message", "Envelope created"),
                        new AuditLogDetail("envelopeName", savedEnvelope.getName()),
                        new AuditLogDetail("accountCode", account.getAccountCode())
                )
        );

        log.info("Envelope {} created for account: {}",
                savedEnvelope.getEnvelopeCode(), account.getAccountCode());

        return mapper.toDto(savedEnvelope);
    }

    // ══════════════════════════════════════════════════════════
    //  CONSULTAR ENVELOPES
    // ══════════════════════════════════════════════════════════

    @Override
    @Transactional(readOnly = true)
    public EnvelopeResponseDto getActiveEnvelope(String envelopeCode, String userEmail) {
        User user = userService.getEntityUserByEmail(userEmail);
        Envelope envelope = findActiveByEnvelopeCode(envelopeCode);

        validateOwnership(envelope, user,AuditAction.ENVELOPE_READ);

        return mapper.toDto(envelope);
    }

    @Override
    @Transactional(readOnly = true)
    public List<EnvelopeResponseDto> getMyEnvelopes(String userEmail) {
        User user = userService.getEntityUserByEmail(userEmail);

        List<Envelope> envelopes = repository
                .findByAccount_User_IdAndStatus(user.getId(), EnvelopeStatus.ACTIVE);

        return envelopes.stream()
                .map(mapper::toDto)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<EnvelopeResponseDto> getActiveAllByAccountCode(String accountCode, String userEmail) {
        User user = userService.getEntityUserByEmail(userEmail);
        Account account = accountService.findAccountWithUserByAccountCode(accountCode);

        // Validar ownership
        if (!account.getUser().getId().equals(user.getId())) {
            throw new UnauthorizedException("You don't own this account");
        }

        List<Envelope> envelopes = repository.findAllActiveByAccountCodeWithAccount(accountCode);

        return envelopes.stream()
                .map(mapper::toDto)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<EnvelopeResponseDto> findAllByStatus(EnvelopeStatus status, String userEmail) {
        User user = userService.getEntityUserByEmail(userEmail);

        List<Envelope> envelopes = repository.findByStatusAndUserId(status, user.getId());

        return envelopes.stream()
                .map(mapper::toDto)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<EnvelopeResponseDto> findAllActiveByType(EnvelopeType type, String userEmail) {
        User user = userService.getEntityUserByEmail(userEmail);

        List<Envelope> envelopes = repository.findActiveByTypeAndUserId(type, user.getId());

        return envelopes.stream()
                .map(mapper::toDto)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<EnvelopeResponseDto> findAllByStatusAndAccountCode(
            EnvelopeStatus status,
            String accountCode,
            String userEmail
    ) {
        User user = userService.getEntityUserByEmail(userEmail);
        Account account = accountService.findAccountWithUserByAccountCode(accountCode);

        // Validar ownership
        if (!account.getUser().getId().equals(user.getId())) {
            throw new UnauthorizedException("You don't own this account");
        }

        List<Envelope> envelopes = repository
                .findByAccount_AccountCodeAndStatus(accountCode, status);

        return envelopes.stream()
                .map(mapper::toDto)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<EnvelopeResponseDto> getActiveByCreatedAfter(
            LocalDateTime createdAfter,
            String accountCode,
            String userEmail
    ) {
        User user = userService.getEntityUserByEmail(userEmail);
        Account account = accountService.findAccountWithUserByAccountCode(accountCode);

        // Validar ownership
        if (!account.getUser().getId().equals(user.getId())) {
            throw new UnauthorizedException("You don't own this account");
        }

        List<Envelope> envelopes = repository
                .findActiveCreatedAfter(createdAfter, accountCode);

        return envelopes.stream()
                .map(mapper::toDto)
                .toList();
    }

    // ══════════════════════════════════════════════════════════
    //  ACTUALIZAR ENVELOPE
    // ══════════════════════════════════════════════════════════

    @Override
    @Transactional
    public EnvelopeResponseDto update(EnvelopeUpdateDto dto, String envelopeCode, String userEmail) {
        User user = userService.getEntityUserByEmail(userEmail);
        Envelope envelope = findActiveWithAccountByCode(envelopeCode);

        validateOwnership(envelope, user,AuditAction.ENVELOPE_UPDATED);

        String oldValues = mapper.toJsonString(envelope);

        mapper.updateEntityFromDto(dto, envelope);

        // Recalcular progreso si cambió target amount
        if (dto.targetAmount() != null) {
            envelope.updateProgress();
        }

        Envelope savedEnvelope = repository.save(envelope);

        String newValues = mapper.toJsonString(savedEnvelope);

        outboxEventRepository.save(new OutboxEvent(
                "Envelope",
                savedEnvelope.getId().toString(),
                "EnvelopeUpdated",
                buildEnvelopePayload(savedEnvelope),
                KafkaTopic.ENVELOPE_EVENTS
        ));

        auditLogService.logSuccess(
                user,
                AuditAction.ENVELOPE_UPDATED,
                AuditEntityType.ENVELOPE,
                savedEnvelope.getId().toString(),
                List.of(
                        new AuditLogDetail("message", "Envelope updated"),
                        new AuditLogDetail("envelopeName", savedEnvelope.getName()),
                        new AuditLogDetail("oldValues", oldValues),
                        new AuditLogDetail("newValues", newValues)
                )
        );

        log.info("Envelope {} updated for account: {}",
                savedEnvelope.getEnvelopeCode(), envelope.getAccount().getAccountCode());

        return mapper.toDto(savedEnvelope);
    }

    @Override
    @Transactional
    public EnvelopeResponseDto updateStatusByAdmin(String envelopeCode, EnvelopeStatus status, String userEmail) {
        User user = userService.getEntityUserByEmail(userEmail);
        Envelope envelope = findActiveWithAccountByCode(envelopeCode);

        validateOwnership(envelope, user,AuditAction.ENVELOPE_STATUS_CHANGED);

        EnvelopeStatus oldStatus = envelope.getStatus();
        envelope.setStatus(status);

        Envelope savedEnvelope = repository.save(envelope);

        auditLogService.logSuccess(
                user,
                AuditAction.ENVELOPE_STATUS_CHANGED,
                AuditEntityType.ENVELOPE,
                savedEnvelope.getId().toString(),
                List.of(
                        new AuditLogDetail("message", "Envelope status changed"),
                        new AuditLogDetail("envelopeName", savedEnvelope.getName()),
                        new AuditLogDetail("oldStatus", oldStatus),
                        new AuditLogDetail("newStatus", status),
                        new AuditLogDetail("oldValues", oldStatus.toString()),
                        new AuditLogDetail("newValues", status.toString())
                )
        );

        return mapper.toDto(savedEnvelope);
    }

    // ══════════════════════════════════════════════════════════
    //  OPERACIONES DE SALDO
    // ══════════════════════════════════════════════════════════

    @Override
    @Transactional
    public EnvelopeResponseDto deposit(EnvelopeDepositDto dto, String userEmail) {
        User user = userService.getEntityUserByEmail(userEmail);
        Envelope envelope = findActiveWithAccountByCode(dto.envelopeCode());
        Account account = envelope.getAccount();
        try {
            String oldValues = mapper.toJsonString(envelope);

            validateOwnership(envelope, user,AuditAction.ENVELOPE_DEPOSIT);

            // Validar que no esté bloqueado
            if (envelope.isLocked()) {
                throw new EnvelopeLockedException(
                        envelope.getEnvelopeCode(), envelope.getLockedUntil(), envelope.getLockReason()
                );
            }


            envelope.deposit(dto.amount());
            // Verificar si alcanzó la meta
            if (envelope.hasReachedGoal() && envelope.getCompletedAt() == null) { // Poner método
                envelope.setCompletedAt(LocalDateTime.now());
                envelope.setStatus(EnvelopeStatus.COMPLETED);
                log.info("Envelope {} reached goal!", envelope.getEnvelopeCode());
                // Agregar enviar notificación
            }
            account.depositFromEnvelope(dto.amount());
            Envelope savedEnvelope = repository.save(envelope);
            String newValues = mapper.toJsonString(savedEnvelope);

            outboxEventRepository.save(new OutboxEvent(
                    "Envelope",
                    savedEnvelope.getId().toString(),
                    "EnvelopeDeposited",
                    buildEnvelopePayload(savedEnvelope),
                    KafkaTopic.ENVELOPE_EVENTS
            ));

            auditLogService.logSuccess(
                    user,
                    AuditAction.ENVELOPE_DEPOSIT,
                    AuditEntityType.ENVELOPE,
                    savedEnvelope.getId().toString(),
                    List.of(
                            new AuditLogDetail("message", "Deposited to envelope"),
                            new AuditLogDetail("amount", dto.amount()),
                            new AuditLogDetail("envelopeName", savedEnvelope.getName()),
                            new AuditLogDetail("description", dto.description()),
                            new AuditLogDetail("oldValues", oldValues),
                            new AuditLogDetail("newValues", newValues)
                    )
            );

            log.info("Deposited {} to envelope {}", dto.amount(), envelope.getEnvelopeCode());

            return mapper.toDto(savedEnvelope);
        } catch (EnvelopeLockedException e) {
            auditLogService.logFailure(
                    user,
                    AuditAction.ENVELOPE_DEPOSIT,
                    AuditEntityType.ENVELOPE,
                    List.of(
                            new AuditLogDetail("message", "Transaction failed: Envelope is locked"),
                            new AuditLogDetail("envelopeCode", envelope.getEnvelopeCode()),
                            new AuditLogDetail("lockedUntil", envelope.getLockedUntil()),
                            new AuditLogDetail("lockReason", envelope.getLockReason())
                    )
            );
            throw e;
        }
    }

    @Override
    @Transactional
    public EnvelopeResponseDto withdraw(EnvelopeWithdrawDto dto, String userEmail) {
        User user = userService.getEntityUserByEmail(userEmail);
        Envelope envelope = findActiveWithAccountByCode(dto.envelopeCode());
        Account account = envelope.getAccount();
        try {
            validateOwnership(envelope, user,AuditAction.ENVELOPE_WITHDRAWAL);
            String oldValues = mapper.toJsonString(envelope);

            // Validar fondos
            if (envelope.getBalance().compareTo(dto.amount()) < 0) {
                auditLogService.logFailure(
                        user,
                        AuditAction.ENVELOPE_WITHDRAWAL,
                        AuditEntityType.ENVELOPE,
                        List.of(
                                new AuditLogDetail("message", "Insufficient funds"),
                                new AuditLogDetail("available", envelope.getBalance()),
                                new AuditLogDetail("requested", dto.amount())
                        )
                );
                throw new EnvelopeInsufficientFundsException(
                        envelope.getBalance(), dto.amount(), envelope.getEnvelopeCode()
                );
            }

            envelope.withdraw(dto.amount());
            account.withdrawFromEnvelope(dto.amount());

            Envelope savedEnvelope = repository.save(envelope);
            String newValues = mapper.toJsonString(savedEnvelope);

            outboxEventRepository.save(new OutboxEvent(
                    "Envelope",
                    savedEnvelope.getId().toString(),
                    "EnvelopeWithdrawn",
                    buildEnvelopePayload(savedEnvelope),
                    KafkaTopic.ENVELOPE_EVENTS
            ));

            auditLogService.logSuccess(
                    user,
                    AuditAction.ENVELOPE_WITHDRAWAL,
                    AuditEntityType.ENVELOPE,
                    savedEnvelope.getId().toString(),
                    List.of(
                            new AuditLogDetail("message", "Withdrew from envelope"),
                            new AuditLogDetail("amount", dto.amount()),
                            new AuditLogDetail("envelopeName", savedEnvelope.getName()),
                            new AuditLogDetail("description", dto.description()),
                            new AuditLogDetail("oldValues", oldValues),
                            new AuditLogDetail("newValues", newValues)
                    )
            );

            log.info("Withdrew {} from envelope {}", dto.amount(), envelope.getEnvelopeCode());

            return mapper.toDto(savedEnvelope);
        }catch (EnvelopeLockedException e) {
            auditLogService.logFailure(
                    user,
                    AuditAction.ENVELOPE_WITHDRAWAL,
                    AuditEntityType.ENVELOPE,
                    List.of(
                            new AuditLogDetail("message", "Transaction failed: Envelope is locked"),
                            new AuditLogDetail("envelopeCode", envelope.getEnvelopeCode()),
                            new AuditLogDetail("lockedUntil", envelope.getLockedUntil()),
                            new AuditLogDetail("lockReason", envelope.getLockReason())
                    )
            );
            throw e;
        }
    }

    // ══════════════════════════════════════════════════════════
    //  ELIMINAR ENVELOPE
    // ══════════════════════════════════════════════════════════

    @Override
    @Transactional
    public void delete(String envelopeCode, String userEmail) {
        User user = userService.getEntityUserByEmail(userEmail);
        Envelope envelope = findActiveWithAccountByCode(envelopeCode);

        String oldValues = mapper.toJsonString(envelope);

        validateOwnership(envelope, user,AuditAction.ENVELOPE_DELETED);

        // Validar que el balance sea 0
        if (envelope.getBalance().compareTo(BigDecimal.ZERO) != 0) {
            auditLogService.logFailure(
                    user,
                    AuditAction.ENVELOPE_DELETED,
                    AuditEntityType.ENVELOPE,
                    List.of(
                            new AuditLogDetail("message", "Deletion failed: Envelope still has a remaining balance"),
                            new AuditLogDetail("envelopeName", envelope.getName()),
                            new AuditLogDetail("envelopeCode", envelope.getEnvelopeCode()),
                            new AuditLogDetail("balance", envelope.getBalance())
                    )
            );
            throw new EnvelopeNotEmptyException(
                    envelope.getEnvelopeCode()
            );
        }

        // Soft delete
        envelope.setStatus(EnvelopeStatus.DELETED);
        repository.save(envelope);

        try {
            outboxEventRepository.save(new OutboxEvent(
                    "Envelope",
                    envelope.getId().toString(),
                    "EnvelopeDeleted",
                    objectMapper.writeValueAsString(Map.of(
                            "envelopeId", envelope.getId(),
                            "envelopeCode", envelope.getEnvelopeCode()
                    )),
                    KafkaTopic.ENVELOPE_EVENTS
            ));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize event payload", e);
        }

        auditLogService.logSuccess(
                user,
                AuditAction.ENVELOPE_DELETED,
                AuditEntityType.ENVELOPE,
                envelope.getId().toString(),
                List.of(
                        new AuditLogDetail("message", "Envelope deleted"),
                        new AuditLogDetail("envelopeName", envelope.getName()),
                        new AuditLogDetail("oldValues", oldValues)
                )
        );

        log.info("Envelope {} deleted by user {}", envelope.getEnvelopeCode(), userEmail);
    }

    // ══════════════════════════════════════════════════════════
    //  MÉTODOS AUXILIARES INTERNOS
    // ══════════════════════════════════════════════════════════

    @Override
    @Transactional(readOnly = true)
    public Envelope findActiveByEnvelopeCode(String envelopeCode) {
        return repository.findActiveByEnvelopeCode(envelopeCode)
                .orElseThrow(() -> new EnvelopeNotFoundException(envelopeCode));
    }

    @Override
    @Transactional(readOnly = true)
    public Envelope findActiveWithAccountByCode(String envelopeCode) {
        return repository.findActiveByEnvelopeCodeWithAccount(envelopeCode)
                .orElseThrow(() -> new EnvelopeNotFoundException(envelopeCode));
    }

    private String buildEnvelopePayload(Envelope envelope) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "envelopeId", envelope.getId().toString(),
                    "envelopeCode", envelope.getEnvelopeCode(),
                    "name", envelope.getName(),
                    "type", envelope.getType().name(),
                    "status", envelope.getStatus().name(),
                    "balance", envelope.getBalance()
            ));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize event payload", e);
        }
    }

    private void validateOwnership(Envelope envelope, User user,AuditAction auditAction) {
        if (!envelope.getAccount().getUser().getId().equals(user.getId())) {
            auditLogService.logFailure(
                    user,
                    auditAction,
                    AuditEntityType.ENVELOPE,
                    List.of(
                            new AuditLogDetail("message", "Security Violation: User attempted to access Envelope belonging to a different Account"),
                            new AuditLogDetail("userId", user.getId()),
                            new AuditLogDetail("userEmail", user.getEmail()),
                            new AuditLogDetail("envelopeCode", envelope.getEnvelopeCode()),
                            new AuditLogDetail("accountCode", envelope.getAccount().getAccountCode())
                    )
            );
            throw new UnauthorizedException("You don't own this envelope");
        }
    }
}
