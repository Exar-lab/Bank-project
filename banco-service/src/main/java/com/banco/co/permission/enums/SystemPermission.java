package com.banco.co.permission.enums;

import lombok.Getter;

@Getter
public enum SystemPermission {

    // ── ACCOUNTS ──────────────────────────────────
    ACCOUNT_READ("account:read", "Ver información de cuentas"),
    ACCOUNT_CREATE("account:create", "Crear cuentas"),
    ACCOUNT_UPDATE("account:update", "Actualizar cuentas"),
    ACCOUNT_CLOSE("account:close", "Cerrar cuentas"),
    ACCOUNT_BLOCK("account:block", "Bloquear cuentas"),
    ACCOUNT_BALANCE_READ("account:balance:read", "Ver saldo"),

    // ── TRANSACTIONS ──────────────────────────────
    TRANSACTION_READ("transaction:read", "Ver transacciones propias"),
    TRANSACTION_READ_ALL("transaction:read:all", "Ver todas las transacciones"),
    TRANSACTION_CREATE("transaction:create", "Crear transacciones"),
    TRANSACTION_REVERSE("transaction:reverse", "Revertir transacciones"),
    TRANSACTION_APPROVE("transaction:approve", "Aprobar transacciones"),
    TRANSACTION_EXPORT("transaction:export", "Exportar transacciones"),

    // ── CARDS ─────────────────────────────────────
    CARD_READ("card:read", "Ver tarjetas"),
    CARD_CREATE("card:create", "Crear tarjetas"),
    CARD_BLOCK("card:block", "Bloquear tarjetas"),
    CARD_LIMIT_UPDATE("card:limit:update", "Cambiar límites"),
    CARD_PIN_RESET("card:pin:reset", "Resetear PIN"),

    // ── USERS ─────────────────────────────────────
    USER_READ("user:read", "Ver usuarios"),
    USER_READ_SENSITIVE("user:read:sensitive", "Ver datos sensibles"),
    USER_CREATE("user:create", "Crear usuarios"),
    USER_UPDATE("user:update", "Actualizar usuarios"),
    USER_DELETE("user:delete", "Eliminar usuarios"),
    USER_SUSPEND("user:suspend", "Suspender usuarios"),
    USER_ROLE_ASSIGN("user:role:assign", "Asignar roles a usuarios"),

    // ── FRAUD ─────────────────────────────────────
    FRAUD_ALERT_READ("fraud:alert:read", "Ver alertas de fraude"),
    FRAUD_ALERT_RESOLVE("fraud:alert:resolve", "Resolver alertas"),
    FRAUD_RULES_UPDATE("fraud:rules:update", "Modificar reglas de fraude"),

    // ── REPORTS ───────────────────────────────────
    REPORT_BASIC("report:basic", "Reportes básicos"),
    REPORT_ADVANCED("report:advanced", "Reportes avanzados"),
    REPORT_FINANCIAL("report:financial", "Reportes financieros"),

    // ── ADMIN ─────────────────────────────────────
    ADMIN_SYSTEM_CONFIG("admin:system:config", "Configuración del sistema"),
    ADMIN_ROLE_MANAGE("admin:role:manage", "Gestionar roles"),
    ADMIN_AUDIT_READ("admin:audit:read", "Ver logs de auditoría"),
    ADMIN_BANK_MANAGE("admin:bank:manage", "Gestionar banco");

    private final String scope;
    private final String description;

    SystemPermission(String scope, String description) {
        this.scope = scope;
        this.description = description;
    }
}