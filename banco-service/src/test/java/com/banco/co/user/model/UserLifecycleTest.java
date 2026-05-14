package com.banco.co.user.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class UserLifecycleTest {

    @Test
    void testGenerateData_WhenGeneratedFieldsAreNull_FillsUserCodeAndUsername() {
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
    void testGenerateData_WhenGeneratedFieldsAlreadyExist_PreservesExistingValues() {
        User user = new User();
        user.setFistName("Ana");
        user.setUserCode("USR-EXISTING-001");
        user.setUsername("ana.existing");

        user.generateData();

        assertThat(user.getUserCode()).isEqualTo("USR-EXISTING-001");
        assertThat(user.getUsername()).isEqualTo("ana.existing");
    }
}
