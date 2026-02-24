package com.banco.co.card.enums;

public enum CardStatus {
    INACTIVE,      // Creada pero no activada
    ACTIVE,        // Funcional
    BLOCKED,       // Bloqueada temporalmente
    EXPIRED,       // Venció
    STOLEN,        // Reportada como robada
    LOST,          // Reportada como perdida
    CLOSED         // Cerrada permanentemente
}
