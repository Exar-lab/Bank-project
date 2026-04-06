package com.banco.co.security.controller;

import com.banco.co.account.controller.AccountAdminController;
import com.banco.co.account.controller.AccountController;
import com.banco.co.card.controller.CardAdminController;
import com.banco.co.card.controller.CardController;
import com.banco.co.envelope.controller.EnvelopeController;
import com.banco.co.user.controller.PublicUserController;
import com.banco.co.user.controller.UserAdminController;
import com.banco.co.user.controller.UserController;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class ControllerSecurityAnnotationsTest {

    @Test
    void testAccountControllerScopes_AreAlignedWithReadWriteModel() throws Exception {
        assertPreAuthorizeContains(AccountController.class, "getMyAccounts", "SCOPE_account:read");
        assertPreAuthorizeContains(AccountController.class, "getAccount", "SCOPE_account:read");
        assertPreAuthorizeContains(AccountController.class, "createAccount", "SCOPE_account:write");
        assertPreAuthorizeContains(AccountController.class, "updateAccount", "SCOPE_account:write");
        assertPreAuthorizeContains(AccountController.class, "closeAccount", "SCOPE_account:write");
    }

    @Test
    void testEnvelopeControllerScopes_AreAlignedWithReadWriteModel() throws Exception {
        assertPreAuthorizeContains(EnvelopeController.class, "getMyEnvelopes", "SCOPE_envelope:read");
        assertPreAuthorizeContains(EnvelopeController.class, "getByCode", "SCOPE_envelope:read");
        assertPreAuthorizeContains(EnvelopeController.class, "create", "SCOPE_envelope:write");
        assertPreAuthorizeContains(EnvelopeController.class, "deposit", "SCOPE_envelope:write");
        assertPreAuthorizeContains(EnvelopeController.class, "withdraw", "SCOPE_envelope:write");
    }

    @Test
    void testUserControllersScopes_AreAlignedWithReadWriteModel() throws Exception {
        assertPreAuthorizeContains(UserController.class, "me", "SCOPE_user:read");
        assertPreAuthorizeContains(UserController.class, "updateMe", "SCOPE_user:write");
        assertPreAuthorizeContains(UserController.class, "updatePassword", "SCOPE_user:write");
        assertPreAuthorizeContains(UserController.class, "deleteMe", "SCOPE_user:write");

        assertPreAuthorizeContains(UserAdminController.class, "getById", "SCOPE_user:read");
        assertPreAuthorizeContains(UserAdminController.class, "createEmployee", "SCOPE_user:write");
        assertPreAuthorizeContains(UserAdminController.class, "updateStatus", "SCOPE_user:write");

        assertPreAuthorizeContains(PublicUserController.class, "register", "permitAll()");
    }

    @Test
    void testCardControllersScopes_KeepCompatibilityAndReadWriteModel() throws Exception {
        assertPreAuthorizeContains(CardController.class, "getMyCards", "SCOPE_card:read");
        assertPreAuthorizeContains(CardController.class, "createCard", "SCOPE_card:write");
        assertPreAuthorizeContains(CardController.class, "blockCard", "SCOPE_card:write");
        assertPreAuthorizeContains(CardController.class, "updateLimits", "SCOPE_card:write");
        assertPreAuthorizeContains(CardController.class, "updateLimits", "card:limit:update");

        assertPreAuthorizeContains(CardAdminController.class, "changeStatus", "SCOPE_card:write");
        assertPreAuthorizeContains(CardAdminController.class, "changeStatus", "SYSTEM_ADMIN");
        assertPreAuthorizeContains(AccountAdminController.class, "updateStatus", "SCOPE_account:write");
    }

    private void assertPreAuthorizeContains(Class<?> controllerClass, String methodName, String expectedExpressionPart)
            throws Exception {
        Method method = findFirstMethodByName(controllerClass, methodName);
        PreAuthorize annotation = method.getAnnotation(PreAuthorize.class);

        assertThat(annotation)
                .as("Method %s.%s should have @PreAuthorize", controllerClass.getSimpleName(), methodName)
                .isNotNull();
        assertThat(annotation.value())
                .as("Method %s.%s should contain '%s'", controllerClass.getSimpleName(), methodName, expectedExpressionPart)
                .contains(expectedExpressionPart);
    }

    private Method findFirstMethodByName(Class<?> type, String methodName) {
        for (Method method : type.getDeclaredMethods()) {
            if (method.getName().equals(methodName)) {
                return method;
            }
        }
        throw new IllegalArgumentException("Method not found: " + type.getSimpleName() + "." + methodName);
    }
}
