---
name: java-dependency-injection
description: >
  Constructor-only injection in banco-service via @RequiredArgsConstructor. Never @Autowired on fields or setters.
  Trigger: When creating Spring beans (@Service, @Repository, @Component, @RestController) or injecting dependencies.
license: Apache-2.0
metadata:
  author: gentleman-programming
  version: "1.0"
---

## When to Use

Load this skill whenever you are:
- Creating a new `@Service`, `@Component`, `@RestController`, or `@Configuration` bean
- Adding a dependency to an existing bean
- Writing tests that need to inject mocks
- Seeing `@Autowired` on a field anywhere in the codebase

---

## Critical Patterns

### Rule 1: Constructor Injection via @RequiredArgsConstructor

`@RequiredArgsConstructor` (Lombok) generates a constructor for all `final` fields. This is the standard pattern.

```java
// CORRECT — Lombok generates constructor for all final fields
@Service
@RequiredArgsConstructor
@Slf4j
public class AccountService implements IAccountService {
    private final IAccountRepository accountRepository;
    private final IAuditLogService auditLogService;
    private final IAccountMapper mapper;
    // No @Autowired needed — Spring finds the single constructor automatically
}
```

### Rule 2: Explicit Constructor When You Need Custom Logic

Use explicit constructor when you need validation or computation at injection time:

```java
@Service
@Slf4j
public class JwtUtils {
    private final Algorithm algorithm;
    private final long accessTokenExpiry;
    private final long refreshTokenExpiry;

    // Explicit constructor — performs setup logic
    public JwtUtils(
        @Value("${jwt.secret}") String secret,
        @Value("${jwt.access-expiry-minutes:15}") long accessExpiryMinutes,
        @Value("${jwt.refresh-expiry-days:30}") long refreshExpiryDays
    ) {
        this.algorithm = Algorithm.HMAC256(secret);
        this.accessTokenExpiry = accessExpiryMinutes * 60 * 1000;
        this.refreshTokenExpiry = refreshExpiryDays * 24 * 60 * 60 * 1000;
    }
}
```

### Rule 3: Inject Interfaces, Not Implementations

Always declare the dependency as the interface type, never the concrete class:

```java
// CORRECT — injects the interface
@RequiredArgsConstructor
public class AccountService implements IAccountService {
    private final IAccountRepository accountRepository;  // interface
    private final IAuditLogService auditLogService;      // interface
    private final IAccountMapper mapper;                 // interface (MapStruct generates impl)
}

// WRONG — injects the concrete class
public class AccountService {
    @Autowired
    private AccountRepositoryImpl accountRepository;  // tied to implementation
}
```

### Rule 4: Never @Autowired on Fields

Field injection bypasses the constructor, making the class impossible to test without a full Spring context.

```java
// WRONG — field injection
@Service
public class AccountService {
    @Autowired
    private IAccountRepository accountRepository;  // can't be set in unit tests without Spring
}

// WRONG — setter injection
@Service
public class AccountService {
    private IAccountRepository accountRepository;

    @Autowired
    public void setAccountRepository(IAccountRepository repo) {
        this.accountRepository = repo;
    }
}
```

### Rule 5: `@Configuration` Beans Use Constructor or `@Bean` Method Injection

```java
// CORRECT — configuration bean via constructor
@Configuration
@RequiredArgsConstructor
public class SecurityConfig {
    private final JwtTokenValidator jwtTokenValidator;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
            .addFilterBefore(jwtTokenValidator, BasicAuthenticationFilter.class)
            .build();
    }
}

// CORRECT — @Bean method with parameter injection
@Configuration
public class AppConfig {
    @Bean
    public IAccountMapper accountMapper(AccountMapperImpl impl) {
        return impl;  // MapStruct generates impl
    }
}
```

---

## Code Examples

### Unit Test — Constructor Injection Enables Pure Mockito

With constructor injection, tests don't need Spring context at all:

```java
// CORRECT — zero Spring context, pure Mockito
class AccountServiceTest {
    private IAccountRepository accountRepository;
    private IAuditLogService auditLogService;
    private IAccountMapper mapper;
    private AccountService accountService;

    @BeforeEach
    void setUp() {
        accountRepository = mock(IAccountRepository.class);
        auditLogService = mock(IAuditLogService.class);
        mapper = mock(IAccountMapper.class);
        // Constructor injection — this is why we love it
        accountService = new AccountService(accountRepository, auditLogService, mapper);
    }

    @Test
    void testDeposit_ValidAmount_ReturnsUpdatedAccount() {
        // given
        Account account = Account.builder().id(UUID.randomUUID()).balance(BigDecimal.valueOf(100)).build();
        when(accountRepository.findActiveByCode("ACC-001")).thenReturn(Optional.of(account));
        when(accountRepository.save(any())).thenReturn(account);
        when(mapper.toDto(any())).thenReturn(new AccountResponseDto(...));
        // when
        AccountResponseDto result = accountService.deposit("ACC-001", new DepositRequestDto(BigDecimal.TEN, "USD"));
        // then
        assertThat(result).isNotNull();
        verify(auditLogService).log(eq(AuditEvent.DEPOSIT), any(), any());
    }
}
```

### @Configuration with Multiple Beans

```java
@Configuration
@RequiredArgsConstructor
public class SecurityConfig {
    private final JwtTokenValidator jwtTokenValidator;
    private final UserDetailsService userDetailsService;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/v1/auth/**").permitAll()
                .anyRequest().authenticated())
            .addFilterBefore(jwtTokenValidator, BasicAuthenticationFilter.class)
            .build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}
```

---

## Anti-Patterns

| Anti-pattern | Problem | Fix |
|---|---|---|
| `@Autowired` on field | Requires Spring reflection; not testable without context | Constructor injection via `@RequiredArgsConstructor` |
| `@Autowired` on setter | Same problem; also allows mutation after construction | Constructor injection |
| Non-final injected fields | Bean state can be changed accidentally | Declare dependencies `final` |
| Concrete class injection | Couples to implementation; breaks OCP | Inject interface type |
| `@Autowired` constructor (explicit) | Redundant since Spring 4.3 — Spring auto-detects single constructor | Remove `@Autowired` from constructor |
| Circular dependencies | Usually a design smell | Split responsibilities into a third bean, or use `ApplicationEventPublisher` |

---

## Resources

- `skill-hexagonal-architecture.md` — Service structure and layer responsibilities
- `skill-junit5-testing-patterns.md` — How constructor injection enables clean unit tests
- Spring docs on dependency injection: https://docs.spring.io/spring-framework/reference/core/beans/dependencies/factory-collaborators.html
