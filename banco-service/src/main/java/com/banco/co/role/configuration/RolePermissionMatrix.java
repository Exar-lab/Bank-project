package com.banco.co.role.configuration;

import com.banco.co.permission.enums.SystemPermission;
import com.banco.co.role.enums.SystemRole;
import org.springframework.context.annotation.Configuration;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

@Configuration
public class RolePermissionMatrix {

    public static final Map<SystemRole, Set<SystemPermission>> MATRIX = new EnumMap<>(SystemRole.class);

    static {
        // Cliente básico: solo ve sus propias cosas
        MATRIX.put(SystemRole.CUSTOMER_BASIC, Set.of(
                SystemPermission.ACCOUNT_READ,
                SystemPermission.ACCOUNT_BALANCE_READ,
                SystemPermission.TRANSACTION_READ,
                SystemPermission.CARD_READ,
                SystemPermission.CARD_BLOCK,
                SystemPermission.TRANSACTION_CREATE
        ));

        // Cliente premium: puede más operaciones
        MATRIX.put(SystemRole.CUSTOMER_PREMIUM, Set.of(
                SystemPermission.ACCOUNT_READ,
                SystemPermission.ACCOUNT_BALANCE_READ,
                SystemPermission.TRANSACTION_READ,
                SystemPermission.TRANSACTION_EXPORT,
                SystemPermission.CARD_READ,
                SystemPermission.CARD_BLOCK,
                SystemPermission.CARD_LIMIT_UPDATE,
                SystemPermission.TRANSACTION_CREATE
        ));

        // Cajero: opera cuentas pero no las crea
        MATRIX.put(SystemRole.TELLER, Set.of(
                SystemPermission.ACCOUNT_READ,
                SystemPermission.ACCOUNT_BALANCE_READ,
                SystemPermission.TRANSACTION_READ,
                SystemPermission.TRANSACTION_CREATE,
                SystemPermission.CARD_READ,
                SystemPermission.CARD_BLOCK,
                SystemPermission.USER_READ
        ));

        // Analista de fraude: ve todo pero no opera
        MATRIX.put(SystemRole.FRAUD_ANALYST, Set.of(
                SystemPermission.TRANSACTION_READ_ALL,
                SystemPermission.FRAUD_ALERT_READ,
                SystemPermission.FRAUD_ALERT_RESOLVE,
                SystemPermission.FRAUD_RULES_UPDATE,
                SystemPermission.USER_READ,
                SystemPermission.CARD_READ,
                SystemPermission.ADMIN_AUDIT_READ
        ));

        // Auditor: solo lectura de absolutamente todo
        MATRIX.put(SystemRole.AUDITOR, Set.of(
                SystemPermission.ACCOUNT_READ,
                SystemPermission.ACCOUNT_BALANCE_READ,
                SystemPermission.TRANSACTION_READ_ALL,
                SystemPermission.TRANSACTION_EXPORT,
                SystemPermission.USER_READ,
                SystemPermission.USER_READ_SENSITIVE,
                SystemPermission.CARD_READ,
                SystemPermission.FRAUD_ALERT_READ,
                SystemPermission.REPORT_FINANCIAL,
                SystemPermission.ADMIN_AUDIT_READ
        ));

        // Super admin: todo
        MATRIX.put(SystemRole.SUPER_ADMIN,
                Set.of(SystemPermission.values())
        );
    }

    public static Set<SystemPermission> getPermissionsFor(SystemRole role) {
        return MATRIX.getOrDefault(role, Set.of());
    }

    // Verificar si un rol puede asignar otro rol
    // Solo roles de mayor nivel pueden asignar roles de menor nivel
    public static boolean canAssignRole(SystemRole assigner, SystemRole target) {
        return assigner.getPrivilegeLevel() > target.getPrivilegeLevel();
    }
}