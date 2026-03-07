package com.banco.co.envelope.service;

import com.banco.co.account.model.Account;
import com.banco.co.account.service.IAccountService;
import com.banco.co.auditLog.enums.AuditAction;
import com.banco.co.auditLog.enums.AuditEntityType;
import com.banco.co.auditLog.service.IAuditLogService;
import com.banco.co.envelope.dto.*;
import com.banco.co.envelope.enums.EnvelopeStatus;
import com.banco.co.envelope.enums.EnvelopeType;
import com.banco.co.envelope.exception.*;
import com.banco.co.envelope.mapper.IEnvelopeMapper;
import com.banco.co.envelope.model.Envelope;
import com.banco.co.envelope.repository.IEnvelopeRepository;
import com.banco.co.exception.authentication.UnauthorizedException;
import com.banco.co.user.model.User;
import com.banco.co.user.service.user.IUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
@RequiredArgsConstructor
@Service
@Slf4j
public class EnvelopeService implements IEnvelopeService {
    private final IEnvelopeRepository repository;
    private final IAccountService accountService;
    private final IUserService userService;
    private final IEnvelopeMapper mapper;
    private final IAuditLogService auditLogService;

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

        auditLogService.logSuccess(
                user,
                AuditAction.ENVELOPE_CREATED,
                AuditEntityType.ENVELOPE,
                savedEnvelope.getId().toString(),
                String.format("Envelope %s created for account: %s",
                        savedEnvelope.getName(), account.getAccountCode()),
                null,
                null
        );

        log.info("Envelope {} created for account: {}",
                savedEnvelope.getEnvelopeCode(), account.getAccountCode());

        return mapper.toDto(savedEnvelope);
    }

    // ══════════════════════════════════════════════════════════
    //  CONSULTAR ENVELOPES
    // ══════════════════════════════════════════════════════════

    @Override
    public EnvelopeResponseDto getActiveEnvelope(String envelopeCode, String userEmail) {
        User user = userService.getEntityUserByEmail(userEmail);
        Envelope envelope = findActiveByEnvelopeCode(envelopeCode);

        validateOwnership(envelope, user,AuditAction.ENVELOPE_READ);

        return mapper.toDto(envelope);
    }

    @Override
    public List<EnvelopeResponseDto> getMyEnvelopes(String userEmail) {
        User user = userService.getEntityUserByEmail(userEmail);

        List<Envelope> envelopes = repository
                .findByAccount_User_IdAndStatus(user.getId(), EnvelopeStatus.ACTIVE);

        return envelopes.stream()
                .map(mapper::toDto)
                .toList();
    }

    @Override
    public List<EnvelopeResponseDto> getActiveAllByAccountCode(String accountCode, String userEmail) {
        User user = userService.getEntityUserByEmail(userEmail);
        Account account = accountService.findAccountWithUserByAccountCode(accountCode);

        // Validar ownership
        if (!account.getUser().getId().equals(user.getId())) {
            throw new UnauthorizedException("You don't own this account");
        }

        List<Envelope> envelopes = repository.findActiveByAccountCode(accountCode);

        return envelopes.stream()
                .map(mapper::toDto)
                .toList();
    }

    @Override
    public List<EnvelopeResponseDto> findAllByStatus(EnvelopeStatus status, String userEmail) {
        User user = userService.getEntityUserByEmail(userEmail);

        // Filtrar solo envelopes del usuario
        List<Envelope> envelopes = repository.findAllByStatus(status);

        return envelopes.stream()
                .filter(e -> e.getAccount().getUser().getId().equals(user.getId()))
                .map(mapper::toDto)
                .toList();
    }

    @Override
    public List<EnvelopeResponseDto> findAllActiveByType(EnvelopeType type, String userEmail) {
        User user = userService.getEntityUserByEmail(userEmail);

        List<Envelope> envelopes = repository.findActiveByType(type);

        // Filtrar solo envelopes del usuario
        return envelopes.stream()
                .filter(e -> e.getAccount().getUser().getId().equals(user.getId()))
                .map(mapper::toDto)
                .toList();
    }

    @Override
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

        auditLogService.logSuccess(
                user,
                AuditAction.ENVELOPE_UPDATED,
                AuditEntityType.ENVELOPE,
                savedEnvelope.getId().toString(),
                String.format("Envelope %s updated", savedEnvelope.getName()),
                oldValues,
                newValues
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
                String.format("Envelope %s status changed from %s to %s",
                        savedEnvelope.getName(), oldStatus, status),
                oldStatus.toString(),
                status.toString()
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
            auditLogService.logSuccess(
                    user,
                    AuditAction.ENVELOPE_DEPOSIT,
                    AuditEntityType.ENVELOPE,
                    savedEnvelope.getId().toString(),
                    String.format("Deposited %s to envelope %s \n Deposit Description: %s", dto.amount(), savedEnvelope.getName(),dto.description()),
                    oldValues,
                    newValues
            );

            log.info("Deposited {} to envelope {}", dto.amount(), envelope.getEnvelopeCode());

            return mapper.toDto(savedEnvelope);
        }catch (EnvelopeLockedException e) {
            String details = String.format("Transaction failed: Envelope %s is locked until %s. Reason: %s",
                    envelope.getEnvelopeCode(), envelope.getLockedUntil(), envelope.getLockReason());
            auditLogService.logFailure(
                    user,
                    AuditAction.ENVELOPE_WITHDRAWAL,
                    AuditEntityType.ENVELOPE,
                    details
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
                String details = String.format("Insufficient funds. Available: %s, Requested: %s",
                        envelope.getBalance(), dto.amount());
                auditLogService.logFailure(
                        user,
                        AuditAction.ENVELOPE_WITHDRAWAL,
                        AuditEntityType.ENVELOPE,
                        details
                );
                throw new EnvelopeInsufficientFundsException(
                        envelope.getBalance(), dto.amount(), envelope.getEnvelopeCode()
                );
            }

            envelope.withdraw(dto.amount());
            account.withdrawFromEnvelope(dto.amount());

            Envelope savedEnvelope = repository.save(envelope);
            String newValues = mapper.toJsonString(savedEnvelope);

            auditLogService.logSuccess(
                    user,
                    AuditAction.ENVELOPE_WITHDRAWAL,
                    AuditEntityType.ENVELOPE,
                    savedEnvelope.getId().toString(),
                    String.format("Withdrew %s from envelope %s \n Withdraw description: %s", dto.amount(), savedEnvelope.getName(),dto.description()),
                    oldValues,
                    newValues
            );

            log.info("Withdrew {} from envelope {}", dto.amount(), envelope.getEnvelopeCode());

            return mapper.toDto(savedEnvelope);
        }catch (EnvelopeLockedException e) {
            String details = String.format("Transaction failed: Envelope %s is locked until %s. Reason: %s",
                    envelope.getEnvelopeCode(), envelope.getLockedUntil(), envelope.getLockReason());
            auditLogService.logFailure(
                    user,
                    AuditAction.ENVELOPE_WITHDRAWAL,
                    AuditEntityType.ENVELOPE,
                    details
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
            // Detalle específico para el log de auditoría
            String details = String.format(
                    "Deletion failed: Envelope '%s' (%s) still has a remaining balance of %s. The balance must be 0.00 before deletion.",
                    envelope.getName(),
                    envelope.getEnvelopeCode(),
                    envelope.getBalance()
            );
            auditLogService.logFailure(
                    user,
                    AuditAction.ENVELOPE_DELETED,
                    AuditEntityType.ENVELOPE,
                    details
            );
            throw new EnvelopeNotEmptyException(
                    envelope.getEnvelopeCode()
            );
        }

        // Soft delete
        envelope.setStatus(EnvelopeStatus.DELETED);
        repository.save(envelope);

        auditLogService.logSuccess(
                user,
                AuditAction.ENVELOPE_DELETED,
                AuditEntityType.ENVELOPE,
                envelope.getId().toString(),
                String.format("Envelope %s deleted", envelope.getName()),
                oldValues,
                null
        );

        log.info("Envelope {} deleted by user {}", envelope.getEnvelopeCode(), userEmail);
    }

    // ══════════════════════════════════════════════════════════
    //  MÉTODOS AUXILIARES INTERNOS
    // ══════════════════════════════════════════════════════════

    @Override
    public Envelope findActiveByEnvelopeCode(String envelopeCode) {
        return repository.findActiveByEnvelopeCode(envelopeCode)
                .orElseThrow(() -> new EnvelopeNotFoundException(envelopeCode));
    }

    @Override
    public Envelope findActiveWithAccountByCode(String envelopeCode) {
        return repository.findActiveByAccountCodeWithAccount(envelopeCode)
                .orElseThrow(() -> new EnvelopeNotFoundException(envelopeCode));
    }

    private void validateOwnership(Envelope envelope, User user,AuditAction auditAction) {
        if (!envelope.getAccount().getUser().getId().equals(user.getId())) {
            // Construimos un detalle que identifique el conflicto de identidad
            String details = String.format(
                    "Security Violation: User [ID: %s, Email: %s] attempted to access Envelope [Code: %s] " +
                            "belonging to a different Account [Code: %s]",
                    user.getId(),
                    user.getEmail(),
                    envelope.getEnvelopeCode(),
                    envelope.getAccount().getAccountCode()
            );
            auditLogService.logFailure(
                    user,
                    auditAction,
                    AuditEntityType.ENVELOPE,
                    details
            );
            throw new UnauthorizedException("You don't own this envelope");
        }
    }
}
