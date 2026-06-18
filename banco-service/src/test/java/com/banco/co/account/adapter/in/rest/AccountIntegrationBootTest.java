package com.banco.co.account.adapter.in.rest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.jasypt.encryption.StringEncryptor;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 6 boot verification gate.
 * Asserts the full application boots clean with a real MySQL instance and
 * that the hexagonal AccountController endpoints are reachable (401, not 404/500).
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(AccountIntegrationBootTest.TestCryptoConfig.class)
class AccountIntegrationBootTest {

    @TestConfiguration
    static class TestCryptoConfig {
        @Bean
        StringEncryptor stringEncryptor() {
            return new StringEncryptor() {
                @Override
                public String encrypt(String message) {
                    return message;
                }

                @Override
                public String decrypt(String encryptedMessage) {
                    return encryptedMessage;
                }
            };
        }
    }

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
        registry.add("spring.flyway.enabled", () -> "false");
        registry.add("spring.test.database.replace", () -> "NONE");
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void testApplicationContext_StartsClean() {
        // GET /api/v1/accounts without auth → 401, not 404 (endpoint missing) or 500 (boot failure)
        ResponseEntity<String> response = restTemplate.getForEntity("/api/v1/accounts", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void testGetAccountById_Unauthenticated_Returns401() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/v1/accounts/" + java.util.UUID.randomUUID(), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
