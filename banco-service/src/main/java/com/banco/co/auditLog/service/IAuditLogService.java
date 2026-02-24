package com.banco.co.auditLog.service;

import com.banco.co.auditLog.enums.AuditAction;
import com.banco.co.auditLog.enums.AuditEntityType;
import com.banco.co.user.model.User;

public interface IAuditLogService {
    void logSuccess(
            User user,
            AuditAction action,
            AuditEntityType entityType,
            String entityId,
            String details,
            String oldValues,
            String newValues
    );
    void logFailure(
            User user,
            AuditAction action,
            AuditEntityType entityType,
            String details
    );
    void logCritical(
            User user,
            AuditAction action,
            String details
    );
    public void logAnonymous(
            AuditAction action,
            AuditEntityType entityType,
            String entityId,
            String details,
            String oldValues,
            String newValues
    );


}
