package com.banco.co.auditLog.model;

import com.banco.co.auditLog.enums.AuditAction;
import com.banco.co.auditLog.enums.AuditEntityType;
import com.banco.co.auditLog.enums.AuditSeverity;
import com.banco.co.auditLog.enums.AuditStatus;
import com.banco.co.user.model.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
@Entity
@Table(name = "audit_logs", indexes = {
        @Index(name = "idx_user_id", columnList = "user_id"),
        @Index(name = "idx_timestamp", columnList = "timestamp"),
        @Index(name = "idx_action", columnList = "action"),
        @Index(name = "idx_severity", columnList = "severity"),
        @Index(name = "idx_ip_address", columnList = "ip_address")
})
@Getter
@Setter
@NoArgsConstructor
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // ── QUIÉN ─────────────────────────────────────────────────
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;  // Puede ser null para acciones anónimas


    @Column(length = 100)
    private String email;

    @Column(length = 100)
    private String username;

    // ── QUÉ ───────────────────────────────────────────────────
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private AuditAction action;

    @Enumerated(EnumType.STRING)
    @Column(length = 50)
    private AuditEntityType entityType;

    @Column(length = 100)
    private String entityId;

    // ── CUÁNDO ────────────────────────────────────────────────
    @Column(nullable = false)
    private LocalDateTime timestamp;

    // ── DÓNDE ─────────────────────────────────────────────────
    @Column(length = 50)
    private String ipAddress;

    @Column(length = 500)
    private String userAgent;

    @Column(length = 100)
    private String deviceId;

    @Column(length = 20)
    private String deviceType;  // MOBILE, TABLET, DESKTOP

    @Column(length = 100)
    private String locationCity;

    @Column(length = 100)
    private String locationCountry;

    @Column
    private Double latitude;

    @Column
    private Double longitude;

    // ── RESULTADO ─────────────────────────────────────────────
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AuditStatus status;

    @Column(length = 500)
    private String errorMessage;

    // ── SEVERIDAD ─────────────────────────────────────────────
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AuditSeverity severity;

    // ── DETALLES ──────────────────────────────────────────────
    @ElementCollection
    @CollectionTable(name = "audit_log_details", joinColumns = @JoinColumn(name = "audit_log_id"))
    private List<AuditLogDetail> details = new ArrayList<>();
}
