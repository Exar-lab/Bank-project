package com.banco.co.account.adapter.in.rest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Boot gate RED test for Phase 4.1 — asserts the Spring context starts clean with a single
 * AccountEntity mapping on the "account" table. This test FAILS while both
 * com.banco.co.account.model.Account and AccountEntity exist (DuplicateMappingException).
 *
 * After Phase 4.1 atomic commit (deleting legacy Account), this test turns GREEN.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
class AccountContextBootTest {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("banco_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void testContext_StartsWithSingleAccountEntity() {
        // If the context loaded (Spring started), it means:
        // - No BeanCreationException
        // - No DuplicateMappingException (only one @Entity maps to "account" table)
        assertThat(applicationContext).isNotNull();

        // Verify the hexagonal AccountController bean is registered (not just a plain class)
        assertThat(applicationContext.containsBean("accountController")).isTrue();
        assertThat(applicationContext.containsBean("accountAdminController")).isTrue();
    }
}
