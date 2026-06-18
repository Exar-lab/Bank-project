package com.banco.co.account.adapter.in.rest;

import com.banco.co.account.domain.port.in.IAccountUseCase;
import com.banco.co.account.dto.AccountResponseDto;
import com.banco.co.account.enums.AccountStatus;
import com.banco.co.account.enums.AccountType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Task 5.3 — Security slice WebMvcTest for hexagonal AccountController.
 * Verifies @PreAuthorize rules: account:read / SCOPE_account:read for read ops,
 * account:write / SCOPE_account:write for write ops.
 * Uses minimal in-test SecurityFilterChain — no full application context loaded.
 *
 * RED: no security slice test after Phase 4 deleted AccountControllerSecuritySliceWebMvcTest.
 * GREEN: all 200/401/403 scenarios match expected @PreAuthorize behaviour.
 */
@WebMvcTest(controllers = AccountController.class)
@ContextConfiguration(classes = {
        AccountControllerSecuritySliceWebMvcTest.TestApplication.class,
        AccountController.class,
        AccountControllerSecuritySliceWebMvcTest.MethodSecurityTestConfig.class
})
@Import({
        AccountControllerSecuritySliceWebMvcTest.MethodSecurityTestConfig.class
})
class AccountControllerSecuritySliceWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IAccountUseCase accountUseCase;

    private static final UUID ACCOUNT_ID = UUID.randomUUID();
    private static final String ACCOUNT_CODE = "ACC-001";

    // ══════════════════════════════════════════════════════════════════════════
    //  GET /api/v1/accounts — requires account:read or SCOPE_account:read
    //  or CUSTOMER_BASIC / CUSTOMER_PREMIUM role
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @WithMockUser(username = "customer@banco.co", authorities = "SCOPE_account:read")
    void testGetMyAccounts_WithReadScope_Returns200() throws Exception {
        when(accountUseCase.getMyAccounts(anyString())).thenReturn(List.of(sampleResponse()));

        mockMvc.perform(get("/api/v1/accounts"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "customer@banco.co", authorities = "account:read")
    void testGetMyAccounts_WithLegacyReadAuthority_Returns200() throws Exception {
        when(accountUseCase.getMyAccounts(anyString())).thenReturn(List.of(sampleResponse()));

        mockMvc.perform(get("/api/v1/accounts"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "customer@banco.co", roles = "CUSTOMER_BASIC")
    void testGetMyAccounts_WithCustomerBasicRole_Returns200() throws Exception {
        when(accountUseCase.getMyAccounts(anyString())).thenReturn(List.of(sampleResponse()));

        mockMvc.perform(get("/api/v1/accounts"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "customer@banco.co", authorities = "SCOPE_user:read")
    void testGetMyAccounts_WithoutAccountReadScope_Returns403() throws Exception {
        mockMvc.perform(get("/api/v1/accounts"))
                .andExpect(status().isForbidden());
    }

    @Test
    void testGetMyAccounts_WithoutAuthentication_Returns401() throws Exception {
        mockMvc.perform(get("/api/v1/accounts"))
                .andExpect(status().isUnauthorized());
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GET /api/v1/accounts/{id} — requires account:read or SCOPE_account:read
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @WithMockUser(username = "customer@banco.co", authorities = "SCOPE_account:read")
    void testGetAccount_WithReadScope_Returns200() throws Exception {
        when(accountUseCase.getAccount(eq(ACCOUNT_ID), anyString())).thenReturn(sampleResponse());

        mockMvc.perform(get("/api/v1/accounts/{id}", ACCOUNT_ID))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "customer@banco.co", authorities = "SCOPE_card:read")
    void testGetAccount_WithoutAccountScope_Returns403() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/{id}", ACCOUNT_ID))
                .andExpect(status().isForbidden());
    }

    @Test
    void testGetAccount_WithoutAuthentication_Returns401() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/{id}", ACCOUNT_ID))
                .andExpect(status().isUnauthorized());
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  POST /api/v1/accounts — requires account:write or SCOPE_account:write
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @WithMockUser(username = "customer@banco.co", authorities = "SCOPE_account:write")
    void testCreateAccount_WithWriteScope_Returns201() throws Exception {
        when(accountUseCase.createAccount(any(), anyString())).thenReturn(sampleResponse());

        String body = """
                {"accountType":"SAVINGS","currency":"CRC","overdraftLimit":0,"interestRate":0,"documentNumber":"123456789"}
                """;

        mockMvc.perform(post("/api/v1/accounts")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(username = "customer@banco.co", authorities = "account:write")
    void testCreateAccount_WithLegacyWriteAuthority_Returns201() throws Exception {
        when(accountUseCase.createAccount(any(), anyString())).thenReturn(sampleResponse());

        String body = """
                {"accountType":"SAVINGS","currency":"CRC","overdraftLimit":0,"interestRate":0,"documentNumber":"123456789"}
                """;

        mockMvc.perform(post("/api/v1/accounts")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(username = "customer@banco.co", authorities = "SCOPE_account:read")
    void testCreateAccount_WithReadOnlyScope_Returns403() throws Exception {
        String body = """
                {"accountType":"SAVINGS","currency":"CRC","overdraftLimit":0,"interestRate":0,"documentNumber":"123456789"}
                """;

        mockMvc.perform(post("/api/v1/accounts")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    void testCreateAccount_WithoutAuthentication_Returns401() throws Exception {
        String body = """
                {"accountType":"SAVINGS","currency":"CRC","overdraftLimit":0,"interestRate":0,"documentNumber":"123456789"}
                """;

        mockMvc.perform(post("/api/v1/accounts")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isUnauthorized());
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  PUT /api/v1/accounts/{code} — requires account:write
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @WithMockUser(username = "customer@banco.co", authorities = "SCOPE_account:write")
    void testUpdateAccount_WithWriteScope_Returns200() throws Exception {
        when(accountUseCase.updateAccount(eq(ACCOUNT_CODE), any(), anyString())).thenReturn(sampleResponse());

        String body = """
                {"overdraftLimit":0,"interestRate":0,"currency":"USD"}
                """;

        mockMvc.perform(put("/api/v1/accounts/{code}", ACCOUNT_CODE)
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "customer@banco.co", authorities = "SCOPE_account:read")
    void testUpdateAccount_WithReadOnlyScope_Returns403() throws Exception {
        String body = """
                {"overdraftLimit":0,"interestRate":0,"currency":"USD"}
                """;

        mockMvc.perform(put("/api/v1/accounts/{code}", ACCOUNT_CODE)
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isForbidden());
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  DELETE /api/v1/accounts/{id} — requires account:write
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @WithMockUser(username = "customer@banco.co", authorities = "SCOPE_account:write")
    void testCloseAccount_WithWriteScope_Returns204() throws Exception {
        mockMvc.perform(delete("/api/v1/accounts/{id}", ACCOUNT_ID))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(username = "customer@banco.co", authorities = "SCOPE_account:read")
    void testCloseAccount_WithReadOnlyScope_Returns403() throws Exception {
        mockMvc.perform(delete("/api/v1/accounts/{id}", ACCOUNT_ID))
                .andExpect(status().isForbidden());
    }

    @Test
    void testCloseAccount_WithoutAuthentication_Returns401() throws Exception {
        mockMvc.perform(delete("/api/v1/accounts/{id}", ACCOUNT_ID))
                .andExpect(status().isUnauthorized());
    }

    // ── Inner classes ────────────────────────────────────────────────────────

    @TestConfiguration
    @EnableMethodSecurity
    static class MethodSecurityTestConfig {

        @Bean("accountControllerTestSecurityFilterChain")
        SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
            http
                    .csrf(AbstractHttpConfigurer::disable)
                    .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                    .exceptionHandling(exception -> exception
                            .authenticationEntryPoint((request, response, authException) -> response.sendError(401))
                            .accessDeniedHandler((request, response, accessDeniedException) -> response.sendError(403))
                    )
                    .httpBasic(AbstractHttpConfigurer::disable)
                    .formLogin(AbstractHttpConfigurer::disable);

            return http.build();
        }
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApplication {
    }

    // ── Helper ──────────────────────────────────────────────────────────────────

    private AccountResponseDto sampleResponse() {
        return new AccountResponseDto(
                ACCOUNT_CODE,
                "1234567890",
                AccountType.SAVINGS,
                AccountStatus.ACTIVE,
                "CRC",
                new BigDecimal("5000.00"),
                new BigDecimal("5000.00"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                "customer@banco.co",
                LocalDateTime.now(),
                LocalDateTime.now(),
                null
        );
    }
}
