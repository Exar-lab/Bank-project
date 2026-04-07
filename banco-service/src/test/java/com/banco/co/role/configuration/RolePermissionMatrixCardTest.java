package com.banco.co.role.configuration;

import com.banco.co.permission.enums.SystemPermission;
import com.banco.co.role.enums.SystemRole;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pure unit tests — no Spring context, no mocks.
 * Verifies that CARD_CREATE is granted to all roles that must be able to create cards:
 * customers (online self-service) and tellers (in-branch service).
 */
class RolePermissionMatrixCardTest {

    @Test
    void testGetPermissionsFor_CustomerBasic_HasCardCreate() {
        Set<SystemPermission> permissions = RolePermissionMatrix.getPermissionsFor(SystemRole.CUSTOMER_BASIC);

        assertThat(permissions).contains(SystemPermission.CARD_CREATE);
    }

    @Test
    void testGetPermissionsFor_CustomerPremium_HasCardCreate() {
        Set<SystemPermission> permissions = RolePermissionMatrix.getPermissionsFor(SystemRole.CUSTOMER_PREMIUM);

        assertThat(permissions).contains(SystemPermission.CARD_CREATE);
    }

    @Test
    void testGetPermissionsFor_Teller_HasCardCreate() {
        Set<SystemPermission> permissions = RolePermissionMatrix.getPermissionsFor(SystemRole.TELLER);

        assertThat(permissions).contains(SystemPermission.CARD_CREATE);
    }

    @Test
    void testGetPermissionsFor_SystemAdmin_HasCardCreate() {
        Set<SystemPermission> permissions = RolePermissionMatrix.getPermissionsFor(SystemRole.SYSTEM_ADMIN);

        assertThat(permissions).contains(SystemPermission.CARD_CREATE);
    }
}
