package com.banco.co.account.enums;

public enum AccountType {
    // Cuentas estándar
    SAVINGS,            // Ahorros (Antigua AHORROS)
    CHECKING,           // Corriente (Antigua CORRIENTE)
    PAYROLL,            // Nómina (Antigua NOMINA)

    // Cuentas de inversión y plazo
    FIXED_DEPOSIT,      // Certificado de depósito a término (CDT)
    MONEY_MARKET,       // Cuenta de mercado monetario (alto rendimiento)
    INVESTMENT,         // Cuenta de inversión / corretaje

    // Cuentas específicas
    JOINT_ACCOUNT,      // Cuenta conjunta (dos o más titulares)
    BUSINESS,           // Cuenta para empresas/negocios
    STUDENT,            // Cuenta para estudiantes (beneficios especiales)
    TRUST,              // Cuenta de fideicomiso
    ESCROW              // Cuenta de depósito en garantía (para compras grandes/inmuebles)
}
