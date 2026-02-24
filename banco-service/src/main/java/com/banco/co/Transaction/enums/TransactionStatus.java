package com.banco.co.Transaction.enums;

public enum TransactionStatus {
    PENDING,            // Pendiente
    PROCESSING,         // En proceso
    APPROVED,           // Aprobada (pero no completada)
    COMPLETED,          // Completada exitosamente
    FAILED,             // Falló
    REJECTED,           // Rechazada
    REVERSED,           // Revertida
    PENDING_REVIEW,     // Pendiente de revisión (fraude)
    CANCELLED,          // Cancelada
    SCHEDULED           // Programada para después
}
