package com.banco.co.security.token.repository;

import com.banco.co.security.token.enums.RefreshTokenRevocationReason;
import com.banco.co.security.token.model.RefreshToken;
import com.banco.co.user.enums.DocumentType;
import com.banco.co.user.model.User;
import com.banco.co.user.model.UserCredential;
import com.banco.co.user.repository.IUserCredential;
import com.banco.co.user.repository.IUserRepository;
import org.junit.jupiter.api.Test;
import org.jasypt.encryption.StringEncryptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Testcontainers
@ContextConfiguration(classes = com.banco.co.BancoServiceApplication.class)
@Import(RefreshTokenRepositoryIntegrationTest.TestCryptoConfig.class)
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.test.database.replace=NONE",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class RefreshTokenRepositoryIntegrationTest {

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
    @SuppressWarnings("resource")
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.36")
            .withDatabaseName("banco_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void registerDatasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", MYSQL::getDriverClassName);
    }

    @Autowired
    private IUserRepository userRepository;

    @Autowired
    private IUserCredential userCredentialRepository;

    @Autowired
    private IRefreshTokenRepository refreshTokenRepository;

    @Test
    void testFindByJtiForUpdate_WhenTokenExists_ReturnsToken() {
        UserCredential credential = createCredential("lock-token@banco.co");

        RefreshToken token = new RefreshToken();
        token.setUserCredential(credential);
        token.setJti("jti-lock");
        token.setTokenHash(sha256("refresh-lock"));
        token.setExpiresAt(LocalDateTime.now().plusMinutes(30));
        token.setRevoked(false);
        refreshTokenRepository.save(token);

        Optional<RefreshToken> loaded = refreshTokenRepository.findByJtiForUpdate("jti-lock");

        assertThat(loaded).isPresent();
        assertThat(loaded.orElseThrow().getJti()).isEqualTo("jti-lock");
        assertThat(loaded.orElseThrow().isRevoked()).isFalse();
    }

    @Test
    void testRevokeAllActiveByUser_WhenUserHasActiveTokens_RevokesOnlyActiveOnes() {
        UserCredential credential = createCredential("revoke-user@banco.co");

        RefreshToken activeOne = buildToken(credential, "active-jti-1", false, null);
        RefreshToken activeTwo = buildToken(credential, "active-jti-2", false, null);
        RefreshToken alreadyRevoked = buildToken(
                credential,
                "revoked-jti",
                true,
                RefreshTokenRevocationReason.LOGOUT
        );

        refreshTokenRepository.save(activeOne);
        refreshTokenRepository.save(activeTwo);
        refreshTokenRepository.save(alreadyRevoked);

        int affected = refreshTokenRepository.revokeAllActiveByUser(
                credential.getId(),
                RefreshTokenRevocationReason.REUSE_DETECTED,
                LocalDateTime.now()
        );

        assertThat(affected).isEqualTo(2);

        RefreshToken reloadedActiveOne = refreshTokenRepository.findByJti("active-jti-1").orElseThrow();
        RefreshToken reloadedActiveTwo = refreshTokenRepository.findByJti("active-jti-2").orElseThrow();
        RefreshToken reloadedAlreadyRevoked = refreshTokenRepository.findByJti("revoked-jti").orElseThrow();

        assertThat(reloadedActiveOne.isRevoked()).isTrue();
        assertThat(reloadedActiveOne.getRevocationReason()).isEqualTo(RefreshTokenRevocationReason.REUSE_DETECTED);
        assertThat(reloadedActiveOne.getRevokedAt()).isNotNull();

        assertThat(reloadedActiveTwo.isRevoked()).isTrue();
        assertThat(reloadedActiveTwo.getRevocationReason()).isEqualTo(RefreshTokenRevocationReason.REUSE_DETECTED);
        assertThat(reloadedActiveTwo.getRevokedAt()).isNotNull();

        assertThat(reloadedAlreadyRevoked.isRevoked()).isTrue();
        assertThat(reloadedAlreadyRevoked.getRevocationReason()).isEqualTo(RefreshTokenRevocationReason.LOGOUT);
    }

    private UserCredential createCredential(String email) {
        User user = new User();
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        user.setFistName("Token");
        user.setLastName("Tester");
        user.setUsername("token" + suffix.substring(0, 5));
        user.setEmail(email);
        user.setDocumentType(DocumentType.CEDULA);
        user.setDocumentNumber("30" + suffix.substring(0, 6));
        user.setPhoneNumber("+573105" + suffix.substring(0, 6));
        user.setAddress("Test address");
        user.setBirthDate(LocalDate.of(1991, 1, 1));
        user = userRepository.save(user);

        UserCredential credential = new UserCredential();
        credential.setUser(user);
        credential.setEmail(email);
        credential.setPasswordHash("$2a$10$dummyhash");
        credential.setEnabled(true);
        credential.setAccountNonExpired(true);
        credential.setAccountNonLocked(true);
        credential.setCredentialsNonExpired(true);
        return userCredentialRepository.save(credential);
    }

    private RefreshToken buildToken(UserCredential credential, String jti, boolean revoked, RefreshTokenRevocationReason reason) {
        RefreshToken token = new RefreshToken();
        token.setUserCredential(credential);
        token.setJti(jti);
        token.setTokenHash(sha256("token-" + jti));
        token.setExpiresAt(LocalDateTime.now().plusHours(2));
        token.setRevoked(revoked);
        token.setRevocationReason(reason);
        if (revoked) {
            token.setRevokedAt(LocalDateTime.now().minusMinutes(1));
        }
        return token;
    }

    private String sha256(String value) {
        try {
            return java.util.HexFormat.of().formatHex(
                    java.security.MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8))
            );
        } catch (Exception exception) {
            throw new RuntimeException(exception);
        }
    }
}
