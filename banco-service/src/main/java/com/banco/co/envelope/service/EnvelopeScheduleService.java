package com.banco.co.envelope.service;

import com.banco.co.account.model.Account;
import com.banco.co.auditLog.enums.AuditAction;
import com.banco.co.auditLog.enums.AuditEntityType;
import com.banco.co.auditLog.model.AuditLogDetail;
import com.banco.co.auditLog.service.IAuditLogService;
import com.banco.co.envelope.enums.AutoContributeFrequency;
import com.banco.co.envelope.enums.EnvelopeStatus;
import com.banco.co.envelope.model.Envelope;
import com.banco.co.envelope.repository.IEnvelopeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class EnvelopeScheduleService implements IEnvelopeScheduleService {
    private final IEnvelopeRepository envelopeRepository;
    private final IAuditLogService auditLogService;

    /**
     * Ejecuta auto-contribuciones pendientes
     * Se ejecuta todos los días a las 3:00 AM
     */
    @Scheduled(cron = "0 0 3 * * *")  // Segundo Minuto Hora Día Mes DíaSemana, 0 segundos 0 minutos 3 horas * Todos los años * Todos los meses - Todos los días
    @Transactional
    public void processAutoContributions() {

        log.info("Starting auto-contribution processing...");

        // 1. Buscar todos los envelopes que tengan auto-contribución pendiente
        LocalDate today = LocalDate.now();

        List<Envelope> pendingEnvelopes = envelopeRepository
                .findPendingAutoContributions(today);

        log.info("Found {} envelopes with pending auto-contributions",
                pendingEnvelopes.size());

        int successCount = 0;
        int failedCount = 0;

        // 2. Procesar cada envelope
        for (Envelope envelope : pendingEnvelopes) {
            try {
                processAutoContribution(envelope);
                successCount++;
            } catch (Exception e) {
                failedCount++;
                log.error("Failed to process auto-contribution for envelope {}: {}",
                        envelope.getEnvelopeCode(), e.getMessage(), e);

                auditLogService.logFailure(
                        envelope.getAccount().getUser(),
                        AuditAction.ENVELOPE_AUTO_CONTRIBUTION_FAILED,
                        AuditEntityType.ENVELOPE,
                        List.of(
                                new AuditLogDetail("message", "Auto-contribution failed"),
                                new AuditLogDetail("envelopeCode", envelope.getEnvelopeCode()),
                                new AuditLogDetail("error", e.getMessage())
                        )
                );
            }
        }

        log.info("Auto-contribution processing completed. Success: {}, Failed: {}",
                successCount, failedCount);
    }

    /**
     * Procesa la auto-contribución para un envelope específico
     */
    private void processAutoContribution(Envelope envelope) {

        log.debug("Processing auto-contribution for envelope: {}",
                envelope.getEnvelopeCode());

        // 1. Validar que el envelope sigue activo y no está bloqueado
        if (!envelope.getStatus().equals(EnvelopeStatus.ACTIVE)) {
            log.warn("Skipping envelope {} - not active (status: {})",
                    envelope.getEnvelopeCode(), envelope.getStatus());
            return;
        }

        if (envelope.isLocked()) {
            log.warn("Skipping envelope {} - locked until {}",
                    envelope.getEnvelopeCode(), envelope.getLockedUntil());
            return;
        }

        // 2. Validar que la cuenta tenga fondos suficientes
        Account account = envelope.getAccount();
        BigDecimal contributionAmount = envelope.getAutoContributeAmount();

        if (account.getAvailableBalance().compareTo(contributionAmount) < 0) {
            log.warn("Insufficient funds in account {} for envelope {} auto-contribution. " +
                            "Required: {}, Available: {}",
                    account.getAccountCode(), envelope.getEnvelopeCode(),
                    contributionAmount, account.getAvailableBalance());

            auditLogService.logFailure(
                    account.getUser(),
                    AuditAction.ENVELOPE_AUTO_CONTRIBUTION_FAILED,
                    AuditEntityType.ENVELOPE,
                    List.of(
                            new AuditLogDetail("message", "Insufficient funds for auto-contribution"),
                            new AuditLogDetail("envelopeCode", envelope.getEnvelopeCode()),
                            new AuditLogDetail("required", contributionAmount),
                            new AuditLogDetail("available", account.getAvailableBalance())
                    )
            );
            return;  // No procesar, pero tampoco fallar
        }

        // 3. Retirar de la cuenta
        account.withdraw(contributionAmount);

        // 4. Depositar en el envelope
        envelope.deposit(contributionAmount);

        // 5. Verificar si alcanzó la meta
        if (envelope.hasReachedGoal() && envelope.getCompletedAt() == null) {
            envelope.setCompletedAt(LocalDateTime.now());
            log.info("Envelope {} reached goal through auto-contribution!",
                    envelope.getEnvelopeCode());
            // TODO: Enviar notificación al usuario
        }

        // 6. Calcular próxima fecha de contribución
        LocalDate nextDate = calculateNextContributionDate(
                envelope.getAutoContributeFrequency()
        );
        envelope.setNextContributionDate(nextDate);

        // 7. Guardar cambios
        envelopeRepository.save(envelope);

        auditLogService.logSuccess(
                account.getUser(),
                AuditAction.ENVELOPE_AUTO_CONTRIBUTION_PROCESSED,
                AuditEntityType.ENVELOPE,
                envelope.getId().toString(),
                List.of(
                        new AuditLogDetail("message", "Auto-contribution processed"),
                        new AuditLogDetail("amount", contributionAmount),
                        new AuditLogDetail("envelopeName", envelope.getName()),
                        new AuditLogDetail("newBalance", envelope.getBalance()),
                        new AuditLogDetail("nextContribution", nextDate)
                )
        );

        log.info("Auto-contribution processed successfully. Envelope: {}, Amount: {}, " +
                        "New balance: {}, Next: {}",
                envelope.getEnvelopeCode(), contributionAmount,
                envelope.getBalance(), nextDate);
    }

    /**
     * Calcula la próxima fecha de contribución basada en la frecuencia
     */
    private LocalDate calculateNextContributionDate(AutoContributeFrequency frequency) {
        LocalDate today = LocalDate.now();

        return switch (frequency) {
            case DAILY -> today.plusDays(1);
            case WEEKLY -> today.plusWeeks(1);
            case MONTHLY -> today.plusMonths(1);
            case BIWEEKLY -> today.plusWeeks(2);
        };
    }
}
