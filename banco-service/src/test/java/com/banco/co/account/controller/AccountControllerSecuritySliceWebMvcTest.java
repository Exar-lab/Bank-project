package com.banco.co.account.controller;

import com.banco.co.account.dto.AccountResponseDto;
import com.banco.co.account.enums.AccountStatus;
import com.banco.co.account.enums.AccountType;
import com.banco.co.account.service.IAccountService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.beans.factory.annotation.Autowired;
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
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
    private IAccountService accountService;

    @Test
    @WithMockUser(username = "customer@banco.co", authorities = "SCOPE_account:read")
    void testGetMyAccounts_WithReadScope_Returns200() throws Exception {
        when(accountService.getMyAccounts(anyString())).thenReturn(List.of(sampleResponse()));

        mockMvc.perform(get("/api/v1/accounts"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "customer@banco.co", authorities = "account:read")
    void testGetMyAccounts_WithLegacyReadAuthority_Returns200() throws Exception {
        when(accountService.getMyAccounts(anyString())).thenReturn(List.of(sampleResponse()));

        mockMvc.perform(get("/api/v1/accounts"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "customer@banco.co", authorities = "SCOPE_account:read")
    void testGetAccountById_WithReadScope_Returns200() throws Exception {
        UUID accountId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        when(accountService.getAccount(any(UUID.class), anyString())).thenReturn(sampleResponse());

        mockMvc.perform(get("/api/v1/accounts/{id}", accountId))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "customer@banco.co", authorities = "SCOPE_account:read")
    void testCreateAccount_WithReadScopeOnly_Returns403() throws Exception {
        String validBody = """
                {
                  "accountType": "CHECKING",
                  "currency": "USD",
                  "overdraftLimit": 0,
                  "interestRate": 0,
                  "documentNumber": "12345678"
                }
                """;

        mockMvc.perform(post("/api/v1/accounts")
                        .contentType("application/json")
                        .content(validBody))
                .andExpect(status().isForbidden());
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

    @TestConfiguration
    @EnableMethodSecurity
    static class MethodSecurityTestConfig {

        @Bean
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

    private AccountResponseDto sampleResponse() {
        return new AccountResponseDto(
                "ACC-001",
                "00012345",
                AccountType.CHECKING,
                AccountStatus.ACTIVE,
                "USD",
                new BigDecimal("1000.00"),
                new BigDecimal("1000.00"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                "customer@banco.co",
                LocalDateTime.now(),
                LocalDateTime.now(),
                LocalDateTime.now()
        );
    }
}
