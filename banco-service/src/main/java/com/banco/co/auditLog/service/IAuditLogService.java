package com.banco.co.auditLog.service;

import com.banco.co.auditLog.enums.AuditAction;
import com.banco.co.auditLog.enums.AuditEntityType;
import com.banco.co.auditLog.model.AuditLogDetail;
import com.banco.co.user.model.User;
import java.util.List;

public interface IAuditLogService {
    void logSuccess(
            User user,
            AuditAction action,
            AuditEntityType entityType,
            String entityId,
            List<AuditLogDetail> details
    );
    void logFailure(
            User user,
            AuditAction action,
            AuditEntityType entityType,
            List<AuditLogDetail> details
    );
    void logCritical(
            User user,
            AuditAction action,
            List<AuditLogDetail> details
    );
    public void logAnonymous(
            AuditAction action,
            AuditEntityType entityType,
            String entityId,
            List<AuditLogDetail> details
    );


}
