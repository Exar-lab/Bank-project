package com.banco.co.transaction.enums;

public enum TransactionStatus {
    PENDING,          // Fondos bloqueados, esperando análisis IA
    PROCESSING,       // IA aprobó, ejecutando transferencia
    PENDING_REVIEW,   // IA marcó para revisión humana (fondos bloqueados)
    APPROVED,         // Analista aprobó (ahora ejecutar)
    COMPLETED,        // Transacción exitosa
    FAILED,           // Error técnico (fondos desbloqueados)
    REJECTED,         // IA o analista rechazó (fondos desbloqueados)
    CANCELLED,        // Usuario canceló (fondos desbloqueados)
    REVERSED,         // Admin revirtió (fondos devueltos)
    SCHEDULED         // Programada para el futuro
}
