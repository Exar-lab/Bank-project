package com.banco.co.role.enums;

import lombok.Getter;

@Getter
public enum SystemRole {

    // Roles de cliente
    CUSTOMER_BASIC(
            "ROLE_CUSTOMER_BASIC",
            "Cliente con acceso básico",
            1   // Nivel de privilegio
    ),
    CUSTOMER_PREMIUM(
            "ROLE_CUSTOMER_PREMIUM",
            "Cliente premium con acceso ampliado",
            2
    ),

    // Roles internos del banco
    TELLER(
            "ROLE_TELLER",
            "Cajero: puede ver y operar cuentas",
            3
    ),
    ADVISOR(
            "ROLE_ADVISOR",
            "Ejecutivo: puede gestionar clientes",
            4
    ),
    FRAUD_ANALYST(
            "ROLE_FRAUD_ANALYST",
            "Analista de fraude: acceso a logs y alertas",
            5
    ),
    BRANCH_MANAGER(
            "ROLE_BRANCH_MANAGER",
            "Gerente de sucursal",
            6
    ),
    AUDITOR(
            "ROLE_AUDITOR",
            "Auditor: solo lectura de todo",
            7
    ),
    SYSTEM_ADMIN(
            "ROLE_SYSTEM_ADMIN",
            "Administrador del sistema",
            8
    ),
    SUPER_ADMIN(
            "ROLE_SUPER_ADMIN",
            "Super administrador: acceso total",
            9
    );

    private final String roleName;
    private final String description;
    private final int privilegeLevel;

    SystemRole(String roleName, String description, int privilegeLevel) {
        this.roleName = roleName;
        this.description = description;
        this.privilegeLevel = privilegeLevel;
    }

}
