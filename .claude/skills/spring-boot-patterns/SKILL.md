## When to Use

- Writing any Spring Boot service, component, or configuration class
- Defining DTOs for request/response in any layer
- Injecting dependencies in any Spring-managed bean
- Configuring application properties, beans, or auto-configuration
- Integrating MapStruct mappers, Kafka publishers, or security filters

## Critical Patterns

### ✅ Correct Pattern — Constructor Injection

```java
package com.banco.co.account.service;

import com.banco.co.account.dto.CreateAccountDto;
import com.banco.co.account.dto.AccountResponseDto;
import com.banco.co.account.repository.IAccountRepository;
import com.banco.co.account.mapper.AccountMapper;
import org.springframework.stereotype.Service;

@Service
public class AccountService {

    private final IAccountRepository accountRepository;
    private final AccountMapper accountMapper;

    // Constructor injection — testable, immutable, explicit
    public AccountService(IAccountRepository accountRepository, AccountMapper accountMapper) {
        this.accountRepository = accountRepository;
        this.accountMapper = accountMapper;
    }

    public AccountResponseDto createAccount(CreateAccountDto dto) {
        var entity = accountMapper.toEntity(dto);
        var saved = accountRepository.save(entity);
        return accountMapper.toResponseDto(saved);
    }
}
```

### ❌ Common Mistake — @Autowired Field Injection

```java
// NEVER DO THIS
@Service
public class BadAccountService {
    @Autowired                          // hidden dependency, not testable
    private IAccountRepository repo;   // mutable, bypasses final

    @Autowired
    private AccountMapper mapper;
}
```

**Why it's wrong**: `@Autowired` field injection hides dependencies, prevents `final`, makes unit testing require a Spring context or reflection hacks, and violates the Dependency Inversion principle.

---

### ✅ Correct Pattern — Records for DTOs

```java
package com.banco.co.account.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

// Immutable, compact, no boilerplate
public record CreateAccountDto(
    @NotBlank(message = "Account holder is required")
    String accountHolder,

    @Positive(message = "Initial balance must be positive")
    BigDecimal initialBalance,

    @NotBlank(message = "Currency is required")
    String currency
) {}

public record AccountResponseDto(
    String accountId,
    String accountHolder,
    BigDecimal balance,
    String currency,
    String status
) {}
```

### ❌ Common Mistake — Mutable DTO Class

```java
// NEVER DO THIS
@Data                            // generates setters → mutable → unsafe
public class CreateAccountDto {
    private String accountHolder;
    private BigDecimal initialBalance;
}
```

**Why it's wrong**: Mutable DTOs can be modified after validation, require Lombok which hides behavior, and break immutability guarantees. Records are canonical for DTOs in Java 21+.

---

### ✅ Correct Pattern — @ConfigurationProperties for Config

```java
// RECOMMENDED REFACTOR — replaces scattered @Value fields in JwtUtils
package com.banco.co.security.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "security.jwt")   // matches current application.yml keys
public record JwtProperties(
    String secretKey,
    String issuer,
    long accessTokenExpirationMinutes,
    long refreshTokenExpirationDays
) {}

// In main application class or a @Configuration:
// @EnableConfigurationProperties(JwtProperties.class)
```

> **Current state**: `JwtUtils` uses `@Value("${security.jwt.secret-key}")` etc. The record above is the recommended refactor target. Until migrated, use the `@Value` approach from `JwtUtils`.

### ❌ Common Mistake — @Value scattered across multiple fields

```java
// AVOID when you have 3+ related config keys — group into @ConfigurationProperties instead
@Component
public class JwtUtils {
    @Value("${security.jwt.secret-key}")
    private String secretKey;          // scattered, hard to test, no type safety

    @Value("${security.jwt.issuer}")
    private String issuer;

    @Value("${security.jwt.access-token.expiration-minutes}")
    private long expirationMinutes;
}
```

---

### ✅ Correct Pattern — Kafka Producer Configuration

```java
package com.banco.co.transaction.config;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.Map;

@Configuration
public class KafkaProducerConfig {

    private final KafkaProperties kafkaProperties;

    public KafkaProducerConfig(KafkaProperties kafkaProperties) {
        this.kafkaProperties = kafkaProperties;
    }

    @Bean
    public ProducerFactory<String, Object> producerFactory() {
        return new DefaultKafkaProducerFactory<>(Map.of(
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaProperties.bootstrapServers(),
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class
        ));
    }

    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate(ProducerFactory<String, Object> pf) {
        return new KafkaTemplate<>(pf);
    }
}
```

---

### ✅ Correct Pattern — MapStruct Mapper

```java
package com.banco.co.account.mapper;

import com.banco.co.account.dto.CreateAccountDto;
import com.banco.co.account.dto.AccountResponseDto;
import com.banco.co.account.model.Account;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")   // Spring-managed bean
public interface AccountMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", constant = "ACTIVE")
    Account toEntity(CreateAccountDto dto);

    AccountResponseDto toResponseDto(Account account);
}
```

### ❌ Common Mistake — Manual Mapping in Service

```java
// NEVER DO THIS
@Service
public class AccountService {
    public AccountResponseDto createAccount(CreateAccountDto dto) {
        Account account = new Account();
        account.setAccountHolder(dto.accountHolder()); // manual, error-prone
        account.setBalance(dto.initialBalance());
        // ... more manual mapping
        AccountResponseDto response = new AccountResponseDto(
            account.getId().toString(), account.getAccountHolder(), ...
        );
        return response;
    }
}
```

---

### ✅ Correct Pattern — JWT Security Filter Chain

```java
package com.banco.co.security.config;

import com.banco.co.security.config.filter.JwtTokenValidator;
import com.banco.co.utils.JwtUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;

@EnableMethodSecurity
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtUtils jwtUtils;

    @Bean
    public SecurityFilterChain configure(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .addFilterBefore(new JwtTokenValidator(jwtUtils), BasicAuthenticationFilter.class);
        return http.build();
    }
}
```

### ❌ Common Mistake — WebSecurityConfigurerAdapter

```java
// NEVER DO THIS — deprecated since Spring Security 5.7, removed in 6+
@Configuration
public class SecurityConfig extends WebSecurityConfigurerAdapter {
    @Override
    protected void configure(HttpSecurity http) throws Exception { ... }
}
```

## Examples for Banco-Service

- `com.banco.co.account.service.AccountService` — uses constructor injection + MapStruct mapper
- `com.banco.co.transaction.service.TransactionService` — constructor injection, Optional handling
- `com.banco.co.security.config.SecurityConfig` — SecurityFilterChain bean, `JwtTokenValidator` before `BasicAuthenticationFilter`
- `com.banco.co.utils.JwtUtils` — JWT signing/validation via `@Value("${security.jwt.*}")` (refactor target: `JwtProperties` record)
- `com.banco.co.account.dto.CreateAccountDto` — Record DTO with @Valid constraints
- `com.banco.co.account.mapper.AccountMapper` — MapStruct interface with @Mapper(componentModel="spring")

## Best Practices

- ALWAYS use constructor injection. If the constructor has more than 5 parameters, split the service.
- NEVER annotate a DTO class with `@Data`. Use Java records exclusively for DTOs.
- DTOs live in `{feature}/dto/`. Mappers live in `{feature}/mapper/`. Services in `{feature}/service/`.
- @ConfigurationProperties records replace scattered @Value annotations. Enable with `@EnableConfigurationProperties`.
- SecurityFilterChain is the ONLY accepted way to configure Spring Security (Spring Boot 4.x / Security 6+).
- MapStruct mappers use `componentModel = "spring"` so they are injectable via constructor.
- Kafka KafkaTemplate is configured via @Bean in a dedicated config class, not inline in services.
- Spring Boot version is 4.x — use `jakarta.*` (not `javax.*`) for all Jakarta EE imports.
