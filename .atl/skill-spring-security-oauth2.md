---
name: spring-security-oauth2
description: "Spring Security OAuth2 configuration: SecurityFilterChain, JWT validation, role-based access control, CORS"
trigger: "When securing endpoints, validating JWT tokens, or implementing role-based access"
layer: infrastructure
tags:
  - security
  - oauth2
  - jwt
  - authentication
  - rbac
---

# Skill: spring-security-oauth2

## When to Use

**Trigger Context**:
- Implementing OAuth2 security configuration
- Validating JWT tokens in incoming requests
- Implementing role-based access control (@PreAuthorize)
- Extracting authenticated principal from SecurityContextHolder
- Configuring CORS for cross-origin requests
- Preventing CSRF attacks

**Decision Tree**:
- Need to configure security chains? → Create `SecurityFilterChain` @Bean
- Need JWT validation? → Add custom `JwtTokenValidator` filter
- Need role checks? → Use `@PreAuthorize("hasRole('ROLE_*')")` on methods
- Need to extract current user? → Use `SecurityContextHolder.getContext().getAuthentication()`
- Need CORS support? → Configure `CorsConfigurationSource` @Bean

---

## Critical Patterns

### ✅ Correct: SecurityFilterChain with JWT Validator

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
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;

@EnableMethodSecurity  // Enable @PreAuthorize, @Secured
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {
    private final JwtUtils jwtUtils;

    @Bean
    public SecurityFilterChain configure(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)  // Stateless API, no CSRF needed
            .sessionManagement(session -> 
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)  // No sessions
            )
            .addFilterBefore(
                new JwtTokenValidator(jwtUtils), 
                BasicAuthenticationFilter.class  // Run before basic auth
            );
        return http.build();
    }

    @Bean
    public BCryptPasswordEncoder bCryptPasswordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
```

**Why this works**:
- `@EnableMethodSecurity` enables @PreAuthorize annotation
- `STATELESS` policy: each request must include token (no session cookies)
- `csrf(AbstractHttpConfigurer::disable)` safe for stateless APIs
- Custom `JwtTokenValidator` filter runs before BasicAuthenticationFilter
- BCryptPasswordEncoder for hashing passwords

---

### ✅ Correct: Custom JWT Token Validator Filter

```java
package com.banco.co.security.config.filter;

import com.banco.co.utils.JwtUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class JwtTokenValidator extends OncePerRequestFilter {
    
    private final JwtUtils jwtUtils;
    
    public JwtTokenValidator(JwtUtils jwtUtils) {
        this.jwtUtils = jwtUtils;
    }
    
    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        
        // Extract token from Authorization header
        String token = extractTokenFromHeader(request);
        
        if (token != null && jwtUtils.isTokenValid(token)) {
            // Validate and extract principal from JWT
            Authentication authentication = jwtUtils.getAuthenticationFromToken(token);
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }
        
        filterChain.doFilter(request, response);
    }
    
    private String extractTokenFromHeader(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);  // Remove "Bearer " prefix
        }
        return null;
    }
}
```

**Why this works**:
- Extends `OncePerRequestFilter` (runs once per HTTP request)
- Extracts token from `Authorization: Bearer <token>` header
- `JwtUtils.isTokenValid()` checks signature and expiration
- `SecurityContextHolder.getContext().setAuthentication()` stores authenticated principal
- Subsequent @PreAuthorize checks use this authentication

---

### ✅ Correct: Role-Based Access Control with @PreAuthorize

```java
package com.banco.co.account.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/accounts")
public class AccountController {
    
    private final AccountService accountService;
    
    // Public endpoint (no role required)
    @GetMapping("/{id}")
    public AccountResponseDto getAccount(@PathVariable UUID id) {
        return accountService.getAccount(id);
    }
    
    // Only users with ROLE_USER can create accounts
    @PostMapping
    @PreAuthorize("hasRole('ROLE_USER')")
    public AccountResponseDto createAccount(@Valid @RequestBody AccountRequestDto dto) {
        return accountService.createAccount(dto);
    }
    
    // Only users with ROLE_ADMIN can delete
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public void deleteAccount(@PathVariable UUID id) {
        accountService.deleteAccount(id);
    }
    
    // Multiple roles allowed (OR condition)
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ROLE_ADMIN') OR @accountSecurityService.isOwner(#id)")
    public AccountResponseDto updateAccount(
        @PathVariable UUID id,
        @Valid @RequestBody AccountUpdateDto dto
    ) {
        return accountService.updateAccount(id, dto);
    }
}
```

**Why this works**:
- `@PreAuthorize("hasRole('ROLE_*')")` checks if authenticated user has role
- Multiple conditions: `hasRole('X') OR hasRole('Y')`
- `@accountSecurityService.isOwner(#id)` calls custom security bean method
- `#id` is method parameter (SpEL: Spring Expression Language)
- Throws `AccessDeniedException` if check fails (returns 403)

---

### ❌ Wrong: Hardcoded JWT Secret in Code

```java
// WRONG: Secret exposed in source code!
public class JwtUtils {
    private static final String SECRET_KEY = "my-super-secret-key-12345";  // SECURITY RISK
    
    public String generateToken(String username) {
        return Jwts.builder()
            .subject(username)
            .signWith(SignatureAlgorithm.HS256, SECRET_KEY)  // Exposed!
            .compact();
    }
}
```

**Why this fails**:
- Secret is visible in source code repository
- Anyone with repo access knows the secret
- Compromises entire JWT validation scheme
- Secret cannot be rotated without code change
- Violates 12-factor app principles

**Fix**: Use environment variables or configuration:
```java
@Configuration
@RequiredArgsConstructor
public class JwtUtils {
    
    @Value("${jwt.secret}")  // From application.yml or environment
    private String secretKey;
    
    public String generateToken(String username) {
        return Jwts.builder()
            .subject(username)
            .signWith(SignatureAlgorithm.HS256, secretKey.getBytes())
            .compact();
    }
}

// application.yml:
# jwt:
#   secret: ${JWT_SECRET}  # Set via environment variable
```

---

### ❌ Wrong: Missing CORS Configuration

```java
// WRONG: CORS not configured, browser blocks cross-origin requests
@RestController
@RequestMapping("/api/accounts")
public class AccountController {
    
    @GetMapping
    public List<AccountResponseDto> listAccounts() {
        return accountService.listAll();
    }
    // Frontend at https://app.example.com makes request to https://api.example.com
    // Browser blocks it (different origin) → CORS error
}
```

**Why this fails**:
- Frontend JavaScript cannot access API from different origin
- Browser blocks cross-origin requests without CORS headers
- Frontend sees: "Access to XMLHttpRequest has been blocked by CORS policy"
- API appears broken even though it's working

**Fix**: Enable CORS:
```java
@Configuration
public class CorsConfig {
    
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(Arrays.asList("https://app.example.com"));
        config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE"));
        config.setAllowedHeaders(Arrays.asList("*"));
        config.setAllowCredentials(true);
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}

// In SecurityConfig:
@Bean
public SecurityFilterChain configure(HttpSecurity http) throws Exception {
    http
        .cors(cors -> cors.configurationSource(corsConfigurationSource()))
        .csrf(AbstractHttpConfigurer::disable)
        .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
    return http.build();
}
```

---

### ✅ Correct: Extracting Principal from SecurityContextHolder

```java
package com.banco.co.account.service;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

@Service
public class AccountService {
    
    private final IAccountRepository accountRepository;
    private final IAccountMapper accountMapper;
    
    // Get current authenticated user
    private String getCurrentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof UserDetails) {
            UserDetails userDetails = (UserDetails) authentication.getPrincipal();
            return userDetails.getUsername();
        }
        return null;
    }
    
    // Only return accounts belonging to current user
    public List<AccountResponseDto> getMyAccounts() {
        String username = getCurrentUsername();
        List<Account> accounts = accountRepository.findActiveAccountsByUser_Email(username);
        return accounts.stream()
            .map(accountMapper::toDto)
            .collect(toList());
    }
}
```

**Why this works**:
- `SecurityContextHolder` is thread-local, safe to access from service
- `getPrincipal()` returns authenticated user object
- Can extract username, roles, custom claims
- Allows filtering data by authenticated user
- Prevents users from accessing other users' data

---

## References

- Related: [spring-boot-validation](./skill-spring-boot-validation.md) — Validating @RequestBody before processing
- Related: [spring-boot-testing-junit5-complete](./skill-spring-boot-testing-junit5-complete.md) — Testing secured endpoints with @WebMvcTest
- External: [Spring Security Documentation](https://spring.io/projects/spring-security)
- External: [OAuth2 Introduction](https://oauth.net/2/)
- External: [JWT Best Practices](https://tools.ietf.org/html/rfc8725)
