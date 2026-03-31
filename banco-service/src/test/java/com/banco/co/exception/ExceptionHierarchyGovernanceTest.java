package com.banco.co.exception;

import com.banco.co.envelope.exception.EnvelopeException;
import com.banco.co.envelope.exception.EnvelopeNotFoundException;
import com.banco.co.role.exception.RoleException;
import com.banco.co.role.exception.RoleNotFoundException;
import com.banco.co.user.exception.user.UserException;
import com.banco.co.user.exception.user.UserNotFoundException;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;

import static org.assertj.core.api.Assertions.assertThat;

class ExceptionHierarchyGovernanceTest {

    @Test
    void testUserExceptionModifier_WhenIntermediateType_ThenIsAbstract() {
        assertThat(Modifier.isAbstract(UserException.class.getModifiers())).isTrue();
    }

    @Test
    void testRoleExceptionModifier_WhenIntermediateType_ThenIsAbstract() {
        assertThat(Modifier.isAbstract(RoleException.class.getModifiers())).isTrue();
    }

    @Test
    void testEnvelopeExceptionModifier_WhenIntermediateType_ThenIsAbstract() {
        assertThat(Modifier.isAbstract(EnvelopeException.class.getModifiers())).isTrue();
    }

    @Test
    void testSubclassesInstantiation_WhenUsingPublicConstructors_ThenBehaviorRemainsValid() {
        UserNotFoundException userException = new UserNotFoundException("john@banco.co");
        RoleNotFoundException roleException = new RoleNotFoundException("ADMIN");
        EnvelopeNotFoundException envelopeException = new EnvelopeNotFoundException("env-001");

        assertThat(userException.getErrorCode()).isEqualTo("USER_NOT_FOUND");
        assertThat(roleException.getErrorCode()).isEqualTo("ROLE_NOT_FOUND");
        assertThat(envelopeException.getErrorCode()).isEqualTo("ENVELOPE_NOT_FOUND");
        assertThat(envelopeException.getMetadata()).containsEntry("envelopeIdentifier", "env-001");
    }
}
