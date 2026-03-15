package com.banco.co.auditLog.service;

import com.banco.co.auditLog.enums.*;
import com.banco.co.auditLog.model.AuditLogDetail;
import com.banco.co.auditLog.repository.IAuditLogRepository;
import com.banco.co.user.model.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.util.List;
@Service
@RequiredArgsConstructor
@Slf4j
public class AuditLogService implements IAuditLogService {

    private final IAuditLogRepository auditLogRepository;

    private final AuditLogProcessor auditLogProcessor;

    // ══════════════════════════════════════════════════════════
    //  SOBRECARGAS SIMPLIFICADAS
    // ══════════════════════════════════════════════════════════

    public void logSuccess(
            User user,
            AuditAction action,
            AuditEntityType entityType,
            String entityId,
            List<AuditLogDetail> details
    ) {
        auditLogProcessor.log(user, action, entityType, entityId, AuditStatus.SUCCESS, AuditSeverity.INFO, details);
    }

    public void logFailure(
            User user,
            AuditAction action,
            AuditEntityType entityType,
            List<AuditLogDetail> details
    ) {
        auditLogProcessor.log(user, action, entityType, null, AuditStatus.FAILURE, AuditSeverity.WARNING, details);
    }

    public void logCritical(
            User user,
            AuditAction action,
            List<AuditLogDetail> details
    ) {
        auditLogProcessor.log(user, action, AuditEntityType.SECURITY, null, AuditStatus.SUCCESS, AuditSeverity.CRITICAL, details);
    }

    // Para registros anónimos (registro de usuario)
    public void logAnonymous(
            AuditAction action,
            AuditEntityType entityType,
            String entityId,
            List<AuditLogDetail> details
    ) {
        auditLogProcessor.log(null, action, entityType, entityId, AuditStatus.SUCCESS, AuditSeverity.INFO, details);
    }


}
