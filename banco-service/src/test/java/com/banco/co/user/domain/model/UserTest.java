package com.banco.co.user.domain.model;

import com.banco.co.user.enums.DocumentType;
import com.banco.co.user.enums.KycStatus;
import com.banco.co.user.enums.UserStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UserTest {

    // ══════════════════════════════════════════════════════════
    // T6 — Domain model: pure JUnit 5, NO Spring context, NO mocks
    // Naming: test{Method}_{Condition}_{Expected}
    // ══════════════════════════════════════════════════════════

    @Test
    void testGenerateData_WhenFieldsAreNull_FillsUserCodeAndUsername() {
        int currentYear = LocalDate.now().getYear();
        User user = new User();
        user.setFistName("Ana");

        user.generateData();

        assertThat(user.getUserCode())
                .matches("USR-" + currentYear + "-[ABCDEFGHJKLMNPQRSTUVWXYZ23456789]{6}");
        assertThat(user.getUsername())
                .matches("Ana-" + currentYear + "-\\d{10}");
    }

    @Test
    void testGenerateData_WhenFieldsAlreadyPresent_PreservesExistingValues() {
        User user = new User();
        user.setFistName("Ana");
        user.setUserCode("USR-EXISTING-001");
        user.setUsername("ana.existing");

        user.generateData();

        assertThat(user.getUserCode()).isEqualTo("USR-EXISTING-001");
        assertThat(user.getUsername()).isEqualTo("ana.existing");
    }

    @Test
    void testUser_WhenConstructedWithAllFields_ReturnsCorrectValues() {
        UUID id = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        LocalDate birthDate = LocalDate.of(1990, 5, 15);
        LocalDateTime now = LocalDateTime.now();

        User user = new User();
        user.setId(id);
        user.setUserCode("USR-2024-ABC123");
        user.setFistName("Carlos");
        user.setLastName("Gomez");
        user.setUsername("carlos.gomez");
        user.setEmail("carlos@banco.co");
        user.setDocumentNumber("123456789");
        user.setDocumentType(DocumentType.CEDULA);
        user.setBirthDate(birthDate);
        user.setPhoneNumber("+573001234567");
        user.setStatus(UserStatus.ACTIVE);
        user.setKycStatus(KycStatus.VERIFIED);
        user.setAddress("Calle 123");
        user.setRoleId(roleId);
        user.setCreatedDate(now);
        user.setUpdatedDate(now);

        assertThat(user.getId()).isEqualTo(id);
        assertThat(user.getUserCode()).isEqualTo("USR-2024-ABC123");
        assertThat(user.getFistName()).isEqualTo("Carlos");
        assertThat(user.getLastName()).isEqualTo("Gomez");
        assertThat(user.getEmail()).isEqualTo("carlos@banco.co");
        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(user.getKycStatus()).isEqualTo(KycStatus.VERIFIED);
        assertThat(user.getRoleId()).isEqualTo(roleId);
    }

    @Test
    void testUser_WhenDefaultConstructed_HasActiveStatusAndPendingKyc() {
        User user = new User();

        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(user.getKycStatus()).isEqualTo(KycStatus.PENDING);
    }

    @Test
    void testUserSnapshot_WhenCreated_ExposesAllFields() {
        UUID userId = UUID.randomUUID();
        UserSnapshot snapshot = new UserSnapshot(
                userId.toString(),
                "test@banco.co",
                "test.user",
                "CUSTOMER_BASIC"
        );

        assertThat(snapshot.userId()).isEqualTo(userId.toString());
        assertThat(snapshot.email()).isEqualTo("test@banco.co");
        assertThat(snapshot.username()).isEqualTo("test.user");
        assertThat(snapshot.role()).isEqualTo("CUSTOMER_BASIC");
    }

    @Test
    void testUserSnapshot_WhenTwoDifferentSnapshots_AreNotEqual() {
        String userId = UUID.randomUUID().toString();
        UserSnapshot snapshot1 = new UserSnapshot(userId, "a@banco.co", "user.a", "CUSTOMER_BASIC");
        UserSnapshot snapshot2 = new UserSnapshot(userId, "b@banco.co", "user.b", "EMPLOYEE_BASIC");

        assertThat(snapshot1).isNotEqualTo(snapshot2);
        assertThat(snapshot1.email()).isNotEqualTo(snapshot2.email());
    }
}
