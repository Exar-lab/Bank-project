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
// com/banco/co/utils/JwtUtils.java
@Component
@Slf4j
public class JwtUtils {
    // @Value field injection — NOT constructor injection
    @Value("${security.jwt.secret-key}")
    private String secretKey;

    @Value("${security.jwt.issuer}")
    private String issuer;

    @Value("${security.jwt.access-token.expiration-minutes}")
    private int accessTokenExpirationMinutes;

    @Value("${security.jwt.refresh-token.expiration-days}")
    private int refreshTokenExpirationDays;

    // Algorithm is built INSIDE each method — NOT stored as a field
    public String generateAccessToken(Authentication authentication, Map<String, Object> extraClaims) {
        Algorithm algorithm = Algorithm.HMAC256(secretKey);
        SecurityUser securityUser = (SecurityUser) authentication.getPrincipal();
        UserCredential user = securityUser.getUser();

        return JWT.create()
            .withSubject(securityUser.getUsername())
            .withClaim("userId", user.getId().toString())
            .withClaim("email", user.getEmail())
            .withClaim("username", user.getEmail())
            .withClaim("roles", securityUser.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith("ROLE_"))
                .collect(Collectors.toList()))
            .withClaim("scope", securityUser.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> !a.startsWith("ROLE_"))
                .collect(Collectors.toList()))
            .withClaim("type", "access")
            .withIssuedAt(new Date())
            .withExpiresAt(new Date(System.currentTimeMillis()
                + (long) accessTokenExpirationMinutes * 60 * 1000))
            .sign(algorithm);
    }

    public String generateAccessToken(Authentication authentication) {
        return generateAccessToken(authentication, Map.of());
    }

    public String generateRefreshToken(String username, UUID userId) {
        Algorithm algorithm = Algorithm.HMAC256(secretKey);
        return JWT.create()
            .withSubject(username)
            .withClaim("userId", userId.toString())
            .withClaim("type", "refresh")
            .withIssuedAt(new Date())
            .withExpiresAt(new Date(System.currentTimeMillis()
                + (long) refreshTokenExpirationDays * 24 * 60 * 60 * 1000))
            .sign(algorithm);
    }

    public DecodedJWT validateToken(String token) {
        Algorithm algorithm = Algorithm.HMAC256(secretKey);
        try {
            JWTVerifier verifier = JWT.require(algorithm).build();
            return verifier.verify(token);
        } catch (TokenExpiredException ex) {
            throw new com.banco.co.security.exception.TokenExpiredException(ex.getMessage());
        } catch (JWTVerificationException ex) {
            throw new com.banco.co.security.exception.TokenMalformedException(ex.getMessage());
        }
    }

    public DecodedJWT validateAccessToken(String token) {
        DecodedJWT decoded = validateToken(token);
        if (!"access".equals(decoded.getClaim("type").asString())) {
            throw new com.banco.co.security.exception.TokenMalformedException("Not an access token");
        }
        return decoded;
    }

    public DecodedJWT validateRefreshToken(String token) {
        DecodedJWT decoded = validateToken(token);
        if (!"refresh".equals(decoded.getClaim("type").asString())) {
            throw new com.banco.co.security.exception.TokenMalformedException("Not a refresh token");
        }
        return decoded;
    }

    // Extract helpers
    public String getUsername(DecodedJWT jwt)      { return jwt.getSubject(); }
    public UUID   getUserId(DecodedJWT jwt)        { return UUID.fromString(jwt.getClaim("userId").asString()); }
    public String getEmail(DecodedJWT jwt)         { return jwt.getClaim("email").asString(); }
    public List<String> getRoles(DecodedJWT jwt)   { return jwt.getClaim("roles").asList(String.class); }
    public List<String> getScopes(DecodedJWT jwt)  { return jwt.getClaim("scope").asList(String.class); }
    public String getTokenId(DecodedJWT jwt)       { return jwt.getId(); }
    public Date   getExpirationDate(DecodedJWT jwt){ return jwt.getExpiresAt(); }
    public boolean isTokenExpired(DecodedJWT jwt)  { return jwt.getExpiresAt().before(new Date()); }
    public boolean isRefreshToken(DecodedJWT jwt)  { return "refresh".equals(jwt.getClaim("type").asString()); }
    public boolean isAccessToken(DecodedJWT jwt)   { return "access".equals(jwt.getClaim("type").asString()); }
}
```

**Key facts:**
- `@Value` field injection — NOT constructor injection; `Algorithm.HMAC256(secretKey)` is built inside each method
- `generateAccessToken` takes `Authentication` (+ optional extra-claims overload); principal is cast to `SecurityUser`, user fetched via `securityUser.getUser()`
- `generateRefreshToken(String username, UUID userId)` — minimal claims + `type:"refresh"`
- `validateAccessToken` / `validateRefreshToken` call `validateToken` then check the `type` claim
- Claims: `userId` (string), `email`, `username`, `roles` (ROLE_* list), `scope` (non-ROLE_ list), `type` ("access"/"refresh")
- `TokenExpiredException` and `TokenMalformedException` are domain exceptions (extend `AuthenticationException`)

### 2. JwtTokenValidator — Custom Filter

> **Package**: `com.banco.co.security.config.filter.JwtTokenValidator`
> **Registration**: instantiated inline in `SecurityConfig` via
> `http.addFilterBefore(new JwtTokenValidator(jwtUtils), BasicAuthenticationFilter.class)` — NOT a `@Component`.

**Current state: stub** — `doFilterInternal()` body is empty in the codebase.
The implementation below is the **target design** (not yet written).

```java
// com/banco/co/security/config/filter/JwtTokenValidator.java
// NOTE: registered via SecurityConfig, NOT @Component
@RequiredArgsConstructor
@Slf4j
public class JwtTokenValidator extends OncePerRequestFilter {
    private final JwtUtils jwtUtils;

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {

        // TARGET IMPLEMENTATION (currently a stub in codebase):
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
                // Build authentication from JWT claims directly (no UserDetailsService call needed)
                List<SimpleGrantedAuthority> authorities = decoded.getClaim("roles")
                    .asList(String.class).stream()
                    .map(SimpleGrantedAuthority::new)
                    .collect(Collectors.toList());
                UsernamePasswordAuthenticationToken authToken =
                    new UsernamePasswordAuthenticationToken(username, null, authorities);
                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authToken);
            }
        } catch (BankingException ex) {
            // Clear context and continue chain — Spring Security will reject unauthenticated requests
            // at the authorization layer. Do NOT re-throw or the filter chain is broken.
            log.warn("JWT validation failed: {}", ex.getMessage());
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }
}
```

### 3. SecurityUser — UserDetails Implementation

```java
// com/banco/co/user/model/adapter/SecurityUser.java
@RequiredArgsConstructor
@Getter
public class SecurityUser implements UserDetails {
    // Single field — caller accesses full UserCredential via getUser()
    private final UserCredential user;

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        // Each role emits ROLE_{roleName} + all of that role's permission scopes
        return user.getRoles().stream()
            .flatMap(role -> {
                Stream<SimpleGrantedAuthority> roleAuthority =
                    Stream.of(new SimpleGrantedAuthority("ROLE_" + role.getName()));
                Stream<SimpleGrantedAuthority> permissionAuthorities =
                    role.getPermissions().stream()
                        .map(p -> new SimpleGrantedAuthority(p.getScope()));
                return Stream.concat(roleAuthority, permissionAuthorities);
            })
            .collect(Collectors.toList());
    }

    @Override public String getPassword() { return user.getPasswordHash(); }
    @Override public String getUsername() { return user.getEmail(); }

    // NOTE: inverted in source — isAccountNonLocked returns !user.isAccountNonLocked()
    @Override public boolean isAccountNonLocked() { return !user.isAccountNonLocked(); }

    @Override public boolean isAccountNonExpired()    { return true; }
    @Override public boolean isCredentialsNonExpired(){ return true; }
    @Override public boolean isEnabled()              { return true; }
}
```

### 4. SecurityFilterChain — Spring Security Config

```java
// com/banco/co/security/config/SecurityConfig.java
@EnableMethodSecurity
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {
    private final JwtUtils jwtUtils;   // JwtTokenValidator is NOT injected — instantiated inline

    @Bean
    public SecurityFilterChain configure(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            // No authorizeHttpRequests block in current code
            .addFilterBefore(new JwtTokenValidator(jwtUtils), BasicAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public BCryptPasswordEncoder getBCryptPasswordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(
        AuthenticationConfiguration authenticationConfiguration
    ) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }
}
```

**Key decisions:**
- All four class annotations required: `@EnableMethodSecurity`, `@Configuration`, `@EnableWebSecurity`, `@RequiredArgsConstructor`
- Method is named `configure` (NOT `filterChain`)
- No `authorizeHttpRequests` block in current code — authorization is handled entirely via `@PreAuthorize`
- CSRF disabled — stateless REST API with JWT
- Sessions: `STATELESS` — no server-side session
- Filter placed `BEFORE` `BasicAuthenticationFilter`
- `WebSecurityConfigurerAdapter` is NOT used (removed in Spring Security 6)
- Two extra beans: `getBCryptPasswordEncoder()` and `authenticationManager(AuthenticationConfiguration)`

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

### 6. CORS Configuration

When the frontend is on a different origin, add `CorsConfigurationSource` to `SecurityConfig`:

```java
// Optional: only needed for browser-based frontend on different origin
@Bean
public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration config = new CorsConfiguration();
    config.setAllowedOrigins(Arrays.asList("https://app.example.com")); // Never "*" in production
    config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "PATCH"));
    config.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type"));
    config.setAllowCredentials(true);

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", config);
    return source;
}

// Then wire it in SecurityFilterChain:
.cors(cors -> cors.configurationSource(corsConfigurationSource()))
```

**Convention**: Never use `"*"` for allowed origins in production. List explicit origins.

### 7. Extracting Authenticated Principal in Services

```java
// Service layer: extract current user from SecurityContextHolder
private SecurityUser getCurrentUser() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth != null && auth.getPrincipal() instanceof SecurityUser securityUser) {
        return securityUser;
    }
    throw new UnauthorizedException("No authenticated user in context");
}

// Usage: ownership check before returning data
public List<AccountResponseDto> getMyAccounts() {
    SecurityUser currentUser = getCurrentUser();
    String email = currentUser.getUsername(); // returns user email
    UUID userId  = currentUser.getUser().getId();
    return accountRepository.findByUser_Email(email).stream()
        .map(mapper::toDto).toList();
}
```

**Key rules:**
- Access `SecurityContextHolder` ONLY in service layer — NEVER in domain models
- Cast principal to `SecurityUser` (our adapter), NOT to `UserDetails` — gives access to full `UserCredential`
- `getUsername()` returns email (that's what `SecurityUser.getUsername()` returns)

### 8. HashUtils — Hashing Strategies

```java
// com/banco/co/security/securityhasher/HashUtils.java
// Plain utility class — NO @Component, NO Spring injection, ALL methods STATIC
public class HashUtils {
    private static final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(12);

    // For PINs and passwords (one-way, verifiable)
    public static String hashBcrypt(String pin) {
        return encoder.encode(pin);
    }

    public static boolean verifyBcrypt(String pin, String storedHash) {
        return encoder.matches(pin, storedHash);
    }

    // For idempotency keys / content hashes (deterministic, fast — NOT for secrets)
    public static String hashSha256(String pin) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(pin.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    public static boolean verifySha256(String pin, String storedHash) {
        return hashSha256(pin).equals(storedHash);
    }
}
```

**Use BCrypt for:** PINs, passwords — one-way, slow by design (brute-force resistant).
**Use SHA-256 for:** idempotency keys, content hashes — fast, deterministic, not for secrets.

### 9. JasyptEncryptor — Field-Level Encryption

Encrypts sensitive fields at the JPA layer (stored encrypted in DB, decrypted on load).

> **Package**: `com.banco.co.security.cryptoLib.JasyptEncryptor`
> **Wiring**: Injects `org.jasypt.encryption.StringEncryptor` auto-configured by `jasypt-spring-boot-starter`.
> Algorithm and IV settings come from `application.yml` (`jasypt.encryptor.*`) — NOT set in Java code.

```java
// com/banco/co/security/cryptoLib/JasyptEncryptor.java
@Component
@Converter
@RequiredArgsConstructor
public class JasyptEncryptor implements AttributeConverter<String, String> {
    // Provided by jasypt-spring-boot-starter autoconfiguration.
    // Algorithm/IV are configured in application.yml under jasypt.encryptor.*
    // Do NOT instantiate StandardPBEStringEncryptor here.
    private final StringEncryptor stringEncryptor;

    @Override
    public String convertToDatabaseColumn(String number) {
        return stringEncryptor.encrypt(number);
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        return stringEncryptor.decrypt(dbData);
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

**application.yml (drives encryption config — not Java code):**
```yaml
jasypt:
  encryptor:
    password: ${JASYPT_ENCRYPTOR_PASSWORD}
    # algorithm, iv-generator-classname, etc. configured here
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
| `"*"` as `allowedOrigin` in production | Allows any origin — CSRF risk | List explicit origins |
| Accessing `SecurityContextHolder` in domain layer | Domain must be Spring-free | Move principal extraction to service |

---

## Resources

- `skill-hexagonal-architecture.md` — Security layer location in architecture
- `skill-junit5-testing-patterns.md` — `@WebMvcTest` + `@WithMockUser` for controller tests
- Auth0 java-jwt: https://github.com/auth0/java-jwt
- Spring Security method security: https://docs.spring.io/spring-security/reference/servlet/authorization/method-security.html
- Jasypt: http://www.jasypt.org/encrypting-configuration.html
