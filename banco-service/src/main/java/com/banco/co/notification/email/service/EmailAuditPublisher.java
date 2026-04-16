package com.banco.co.notification.email.service;

import com.banco.co.auditLog.enums.AuditAction;
import com.banco.co.auditLog.enums.AuditEntityType;
import com.banco.co.auditLog.model.AuditLogDetail;
import com.banco.co.auditLog.service.IAuditLogService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class EmailAuditPublisher {

    private final IAuditLogService auditLogService;

    public EmailAuditPublisher(IAuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    public void logDeduped(String eventId) {
        auditLogService.logAnonymous(
                AuditAction.EMAIL_ENQUEUE_DEDUPED,
                AuditEntityType.NOTIFICATION,
                eventId,
                List.of(new AuditLogDetail("eventId", eventId))
        );
    }

    public void logSent(UUID userId, String recipientHash) {
        auditLogService.logSuccess(
                null,
                AuditAction.EMAIL_SENT,
                AuditEntityType.USER,
                userId.toString(),
                List.of(new AuditLogDetail("recipientHash", recipientHash))
        );
    }

    public void logDead(String eventId, int attemptCount, String templateName) {
        auditLogService.logCritical(
                null,
                AuditAction.EMAIL_DELIVERY_DEAD,
                List.of(
                        new AuditLogDetail("eventId", eventId),
                        new AuditLogDetail("attemptCount", attemptCount),
                        new AuditLogDetail("templateName", templateName)
                )
        );
    }
}
