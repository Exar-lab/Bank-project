package com.banco.co.auditLog.service;

import com.banco.co.auditLog.enums.AuditAction;
import com.banco.co.auditLog.enums.AuditEntityType;
import com.banco.co.auditLog.enums.AuditSeverity;
import com.banco.co.auditLog.enums.AuditStatus;
import com.banco.co.auditLog.model.AuditLog;
import com.banco.co.auditLog.repository.IAuditLogRepository;
import com.banco.co.user.model.User;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.util.List;
import com.banco.co.auditLog.model.AuditLogDetail;

/**
 * Clase para anotar procesos del auditLog, para que pueda crear los auditLogs en un hilo distinto
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuditLogProcessor {

    private final IAuditLogRepository auditLogRepository;
    /**
     * Registra una acción de auditoría con información del request actual
     *
     * @param user Usuario que realizó la acción (puede ser null para acciones anónimas)
     * @param action Acción realizada
     * @param entityType Tipo de entidad afectada
     * @param entityId ID de la entidad afectada
     * @param status Resultado de la acción
     * @param severity Nivel de severidad
     * @param details Detalles adicionales
     */
    @Async  // Ejecutar en hilo separado para no bloquear el request
    @Transactional(propagation = Propagation.REQUIRES_NEW)  // Transacción independiente
    public void log(
            User user,
            AuditAction action,
            AuditEntityType entityType,
            String entityId,
            AuditStatus status,
            AuditSeverity severity,
            List<AuditLogDetail> details
    ) {
        try {
            AuditLog auditLog = new AuditLog();

            // Usuario (puede ser null para registros anónimos)
            auditLog.setUser(user);
            auditLog.setEmail(user != null ? user.getEmail() : "anonymous");
            auditLog.setUsername(user != null ? user.getUsername() : "anonymous");

            // Acción
            auditLog.setAction(action);
            auditLog.setEntityType(entityType);
            auditLog.setEntityId(entityId);
            auditLog.setStatus(status);
            auditLog.setSeverity(severity);
            
            // Vincular los detalles y la entidad AuditLog
            if (details != null) {
                auditLog.setDetails(details);
            }
            
            auditLog.setTimestamp(LocalDateTime.now());

            // Información del request (IP, User-Agent, etc.)
            enrichWithRequestInfo(auditLog);

            auditLogRepository.save(auditLog);

            log.debug("Audit log created: {} - {} - {}", action, entityType, status);

        } catch (Exception e) {
            // IMPORTANTE: La auditoría NO debe romper el flujo principal
            log.error("Failed to create audit log: {}", e.getMessage(), e);
        }
    }
    private void enrichWithRequestInfo(AuditLog auditLog) {
        try {
            HttpServletRequest request = getCurrentHttpRequest();

            if (request != null) {
                auditLog.setIpAddress(getClientIP(request));
                auditLog.setUserAgent(request.getHeader("User-Agent"));
                auditLog.setDeviceId(request.getHeader("X-Device-Id"));
                auditLog.setDeviceType(extractDeviceType(request.getHeader("User-Agent")));
            }

        } catch (Exception e) {
            log.warn("Could not enrich audit log with request info: {}", e.getMessage());
        }
    }

    // ══════════════════════════════════════════════════════════
    //  UTILIDADES
    // ══════════════════════════════════════════════════════════

    private HttpServletRequest getCurrentHttpRequest() {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();

        return attributes != null ? attributes.getRequest() : null;
    }

    private String getClientIP(HttpServletRequest request) {
        // Verificar si viene de un proxy o load balancer
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        return ip;
    }

    private String extractDeviceType(String userAgent) {
        if (userAgent == null) {
            return "UNKNOWN";
        }

        String ua = userAgent.toLowerCase();

        if (ua.contains("mobile") || ua.contains("android") || ua.contains("iphone")) {
            return "MOBILE";
        } else if (ua.contains("tablet") || ua.contains("ipad")) {
            return "TABLET";
        } else {
            return "DESKTOP";
        }
    }

}
