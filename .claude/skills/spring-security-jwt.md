---
name: spring-security-jwt
description: >
  JWT security implementation for banco-service using Auth0 java-jwt 4.x, custom filter, RBAC, and Jasypt encryption.
  Trigger: When working on authentication, JWT tokens, security filters, RBAC annotations, or field-level encryption.
license: Apache-2.0
metadata:
  author: gentleman-programming
  version: "1.0"
---

## When to Use

Load this skill whenever you are:
- Adding or modifying authentication endpoints (login, refresh, logout)
- Adding `@PreAuthorize` RBAC to controllers
- Working on `JwtUtils`, `JwtTokenValidator`, or `SecurityUser`
- Configuring `SecurityFilterChain`
- Adding encrypted fields to entities (`@Convert`)
- Writing security-related tests

---

## Critical Patterns

### Stack Reality

This project does NOT use Spring OAuth2 Resource Server.
JWT is implemented manually via:
- `com.auth0:java-jwt:4.5.0` — token generation + verification
- `JwtTokenValidator extends OncePerRequestFilter` — custom filter
- `SecurityUser implements UserDetails` — Spring Security principal
- `JasyptEncryptor implements AttributeConverter` — field-level DB encryption

### 1. JwtUtils — Token Generation and Validation

```java
// com/banco/co/security/JwtUtils.java
@Component
@Slf4j
public class JwtUtils {
    private final Algorithm algorithm;
    private final long accessTokenExpiryMs;
    private final long refreshTokenExpiryMs;

    public JwtUtils(
        @Value("${jwt.secret}") String secret,
        @Value("${jwt.access-expiry-minutes:15}") long accessExpiryMinutes,
        @Value("${jwt.refresh-expiry-days:30}") long refreshExpiryDays
    ) {
        this.algorithm = Algorithm.HMAC256(secret);
        this.accessTokenExpiryMs = accessExpiryMinutes * 60 * 1000L;
        this.refreshTokenExpiryMs = refreshExpiryDays * 24 * 60 * 60 * 1000L;
    }

    public String generateAccessToken(SecurityUser user) {
        return JWT.create()
            .withSubject(user.getUsername())
            .withClaim("userId", user.getUserId().toString())
            .withClaim("email", user.getEmail())
            .withClaim("username", user.getUsername())
            .withClaim("roles", user.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith("ROLE_"))
                .collect(Collectors.toList()))
            .withClaim("scope", user.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> !a.startsWith("ROLE_"))
                .collect(Collectors.joining(" ")))
            .withIssuedAt(new Date())
            .withExpiresAt(new Date(System.currentTimeMillis() + accessTokenExpiryMs))
            .sign(algorithm);
    }

    public String generateRefreshToken(SecurityUser user) {
        return JWT.create()
            .withSubject(user.getUsername())
            .withClaim("userId", user.getUserId().toString())
            .withIssuedAt(new Date())
            .withExpiresAt(new Date(System.currentTimeMillis() + refreshTokenExpiryMs))
            .sign(algorithm);
    }

    public DecodedJWT validateToken(String token) {
        try {
            JWTVerifier verifier = JWT.require(algorithm).build();
            return verifier.verify(token);
        } catch (TokenExpiredException ex) {
            throw new com.banco.co.security.exception.TokenExpiredException(ex.getMessage());
        } catch (JWTVerificationException ex) {
            throw new com.banco.co.security.exception.TokenMalformedException(ex.getMessage());
        }
    }
}
```

**Key facts:**
- Algorithm: HMAC256 (symmetric) — secret must be at least 256 bits
- Access token: 15 min expiry, claims: `userId`, `email`, `username`, `roles` (ROLE_* list), `scope` (space-separated permissions)
- Refresh token: 30 day expiry, minimal claims: `userId` only
- `TokenExpiredException` and `TokenMalformedException` are domain exceptions (extend `AuthenticationException`)

### 2. JwtTokenValidator — Custom Filter

```java
// com/banco/co/security/JwtTokenValidator.java
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtTokenValidator extends OncePerRequestFilter {
    private final JwtUtils jwtUtils;
    private final UserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {

        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            String token = authHeader.substring(7);
            DecodedJWT decoded = jwtUtils.validateToken(token);
            String username = decoded.getSubject();

            // Only load if not already authenticated
            if (SecurityContextHolder.getContext().getAuthentication() == null) {
                UserDetails userDetails = userDetailsService.loadUserByUsername(username);
                UsernamePasswordAuthenticationToken authToken =
                    new UsernamePasswordAuthenticationToken(
                        userDetails, null, userDetails.getAuthorities()
                    );
                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authToken);
            }
        } catch (BankingException ex) {
            // Let the exception propagate — SecurityConfig handles unauthenticated responses
            log.warn("JWT validation failed: {}", ex.getMessage());
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }
}
```

### 3. SecurityUser — UserDetails Implementation

```java
// com/banco/co/security/SecurityUser.java
@Getter
public class SecurityUser implements UserDetails {
    private final UUID userId;
    private final String email;
    private final String username;
    private final String password;             // BCrypt-hashed PIN
    private final List<GrantedAuthority> authorities;
    private final boolean accountNonLocked;

    public SecurityUser(UserCredential credential) {
        this.userId = credential.getId();
        this.email = credential.getEmail();
        this.username = credential.getUsername();
        this.password = credential.getHashedPin();
        this.accountNonLocked = credential.getStatus() == UserStatus.ACTIVE;
        this.authorities = buildAuthorities(credential.getRoles(), credential.getPermissions());
    }

    private List<GrantedAuthority> buildAuthorities(
        Set<Role> roles,
        Set<Permission> permissions
    ) {
        List<GrantedAuthority> auths = new ArrayList<>();
        // Roles: ROLE_ADMIN, ROLE_USER, etc.
        roles.forEach(r -> auths.add(new SimpleGrantedAuthority("ROLE_" + r.getName())));
        // Permissions: account:read, transaction:write, etc.
        permissions.forEach(p -> auths.add(new SimpleGrantedAuthority(p.getName())));
        return Collections.unmodifiableList(auths);
    }

    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isAccountNonLocked() { return accountNonLocked; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled() { return accountNonLocked; }
}
```

### 4. SecurityFilterChain — Spring Security Config

```java
// com/banco/co/security/SecurityConfig.java
@Configuration
@EnableMethodSecurity          // enables @PreAuthorize on methods
@RequiredArgsConstructor
public class SecurityConfig {
    private final JwtTokenValidator jwtTokenValidator;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.POST, "/api/v1/auth/login").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/auth/refresh").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/users/register").permitAll()
                .anyRequest().authenticated())
            .addFilterBefore(jwtTokenValidator, BasicAuthenticationFilter.class)
            .build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);   // strength=12 for PINs
    }
}
```

**Key decisions:**
- CSRF disabled — stateless REST API with JWT
- Sessions: `STATELESS` — no server-side session
- Filter placed `BEFORE` `BasicAuthenticationFilter`
- `WebSecurityConfigurerAdapter` is NOT used (removed in Spring Security 6)

### 5. RBAC Annotations

```java
// In controllers — method-level security
@RestController
@RequestMapping("/api/v1/accounts")
@RequiredArgsConstructor
@Validated
public class AccountController {

    // ROLE-based (coarse-grained)
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<AccountResponseDto>> findAll() { ... }

    // Permission-based (fine-grained)
    @GetMapping("/{code}")
    @PreAuthorize("hasAuthority('account:read')")
    public ResponseEntity<AccountResponseDto> findByCode(@PathVariable String code) { ... }

    // Compound: must have both
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') and hasAuthority('account:delete')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) { ... }

    // Own-resource check (programmatic)
    @GetMapping("/my")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<AccountResponseDto>> findMyAccounts(
        @AuthenticationPrincipal SecurityUser currentUser
    ) { ... }
}
```

**Convention:**
- `hasRole('X')` — checks for authority `ROLE_X` (Spring adds ROLE_ prefix automatically)
- `hasAuthority('account:read')` — exact match on the authority string
- Roles are coarse (`ADMIN`, `USER`, `MANAGER`); permissions are fine (`account:read`, `transaction:write`)

### 6. HashUtils — Hashing Strategies

```java
// com/banco/co/security/HashUtils.java
@Component
public class HashUtils {
    private static final int BCRYPT_STRENGTH = 12;

    // For PINs and passwords (one-way, verifiable)
    public String hashPin(String rawPin) {
        return BCrypt.hashpw(rawPin, BCrypt.gensalt(BCRYPT_STRENGTH));
    }

    public boolean verifyPin(String rawPin, String hashedPin) {
        return BCrypt.checkpw(rawPin, hashedPin);
    }

    // For idempotency keys (deterministic, fast)
    public String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
```

**Use BCrypt for:** PINs, passwords — one-way, slow by design (brute-force resistant).
**Use SHA-256 for:** idempotency keys, content hashes — fast, deterministic, not for secrets.

### 7. JasyptEncryptor — Field-Level Encryption

Encrypts sensitive fields at the JPA layer (stored encrypted in DB, decrypted on load).

```java
// com/banco/co/security/JasyptEncryptor.java
@Converter
public class JasyptEncryptor implements AttributeConverter<String, String> {
    private final StandardPBEStringEncryptor encryptor;

    public JasyptEncryptor(@Value("${jasypt.encryptor.password}") String password) {
        this.encryptor = new StandardPBEStringEncryptor();
        this.encryptor.setPassword(password);
        this.encryptor.setAlgorithm("PBEWithMD5AndDES");
    }

    @Override
    public String convertToDatabaseColumn(String attribute) {
        return attribute != null ? encryptor.encrypt(attribute) : null;
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        return dbData != null ? encryptor.decrypt(dbData) : null;
    }
}

// Usage in entity
@Entity
public class Account {
    @Column(name = "account_number")
    @Convert(converter = JasyptEncryptor.class)
    private String accountNumber;   // stored encrypted in DB
}
```

---

## Anti-Patterns

| Anti-pattern | Problem | Fix |
|---|---|---|
| `@Autowired` on `SecurityConfig` fields | Breaks constructor injection rule | Use `@RequiredArgsConstructor` |
| `WebSecurityConfigurerAdapter` extends | Removed in Spring Security 6 | `SecurityFilterChain` bean |
| `JWT.require(...).build().verify(token)` without try/catch | Uncaught `JWTVerificationException` crashes filter | Catch and throw domain `AuthenticationException` |
| `hasRole('ROLE_ADMIN')` | Spring adds ROLE_ prefix — results in `ROLE_ROLE_ADMIN` | `hasRole('ADMIN')` |
| BCrypt for idempotency keys | Slow, non-deterministic — wrong tool | SHA-256 for deterministic hashes |
| SHA-256 for passwords | Fast, crackable — wrong tool | BCrypt strength=12 |
| Storing raw account numbers in DB | PCI compliance violation | `@Convert(converter = JasyptEncryptor.class)` |
| `SecurityContextHolder.getContext()` in domain layer | Domain must not touch Spring Security | Service layer reads principal and passes values to domain |

---

## Resources

- `skill-hexagonal-architecture.md` — Security layer location in architecture
- `skill-junit5-testing-patterns.md` — `@WebMvcTest` + `@WithMockUser` for controller tests
- Auth0 java-jwt: https://github.com/auth0/java-jwt
- Spring Security method security: https://docs.spring.io/spring-security/reference/servlet/authorization/method-security.html
- Jasypt: http://www.jasypt.org/encrypting-configuration.html
