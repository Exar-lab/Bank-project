package com.banco.co.user.repository;

import com.banco.co.permission.enums.SystemPermission;
import com.banco.co.permission.model.Permission;
import com.banco.co.permission.repository.IPermissionRepository;
import com.banco.co.role.enums.SystemRole;
import com.banco.co.role.model.Role;
import com.banco.co.role.repository.IRoleRepository;
import com.banco.co.user.model.User;
import com.banco.co.user.model.UserCredential;
import jakarta.persistence.EntityManagerFactory;
import org.jasypt.encryption.StringEncryptor;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Testcontainers(disabledWithoutDocker = true)
@ContextConfiguration(classes = com.banco.co.BancoServiceApplication.class)
@Import(UserCredentialRepositoryIntegrationTest.TestCryptoConfig.class)
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.properties.hibernate.generate_statistics=true",
        "spring.test.database.replace=NONE",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class UserCredentialRepositoryIntegrationTest {

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
    private IRoleRepository roleRepository;

    @Autowired
    private IPermissionRepository permissionRepository;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private jakarta.persistence.EntityManager entityManager;

    @Test
    void testFindByEmailWithRolesAndPermissions_WhenCredentialExists_LoadsAuthoritiesWithoutNPlusOne() {
        Permission permission = new Permission();
        permission.setName(SystemPermission.ACCOUNT_READ);
        permission.setScope(SystemPermission.ACCOUNT_READ.getScope());
        permission.setResource("account");
        permission.setAction("read");
        permission.setDescription("Can read accounts");
        permission.setActive(true);
        permission.setSystemDefined(true);
        permission = permissionRepository.save(permission);

        Role role = new Role();
        role.setName(SystemRole.CUSTOMER_BASIC);
        role.setDescription("Basic customer");
        role.setPrivilegeLevel(1);
        role.setActive(true);
        role.setSystemDefined(true);
        role.setPermissions(Set.of(permission));
        role = roleRepository.save(role);

        User user = new User();
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        user.setFistName("Test");
        user.setLastName("User");
        user.setEmail("joinfetch@banco.co");
        user.setDocumentType(com.banco.co.user.enums.DocumentType.CEDULA);
        user.setDocumentNumber("20" + suffix.substring(0, 6));
        user.setPhoneNumber("+573002" + suffix.substring(0, 6));
        user.setAddress("Test address");
        user.setBirthDate(LocalDate.of(1990, 1, 1));
        user = userRepository.save(user);

        UserCredential credential = new UserCredential();
        credential.setUser(user);
        credential.setEmail("joinfetch@banco.co");
        credential.setPasswordHash("$2a$10$dummyhash");
        credential.setEnabled(true);
        credential.setAccountNonExpired(true);
        credential.setAccountNonLocked(true);
        credential.setCredentialsNonExpired(true);
        credential.setRoles(Set.of(role));
        userCredentialRepository.save(credential);

        entityManager.flush();
        entityManager.clear();

        SessionFactory sessionFactory = entityManagerFactory.unwrap(SessionFactory.class);
        Statistics statistics = sessionFactory.getStatistics();
        statistics.clear();

        UserCredential loaded = userCredentialRepository
                .findByEmailWithRolesAndPermissions("joinfetch@banco.co")
                .orElseThrow();

        assertThat(loaded.getRoles()).hasSize(1);
        assertThat(loaded.getRoles().iterator().next().getPermissions()).hasSize(1);
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(1L);
    }
}
