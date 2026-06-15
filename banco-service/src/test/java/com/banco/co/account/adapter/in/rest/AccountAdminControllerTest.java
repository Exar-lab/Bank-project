package com.banco.co.account.adapter.in.rest;

import com.banco.co.account.domain.port.in.IAccountUseCase;
import com.banco.co.account.dto.AccountResponseDto;
import com.banco.co.account.enums.AccountStatus;
import com.banco.co.account.enums.AccountType;
import com.banco.co.account.exception.account.AccountNotFoundException;
import com.banco.co.exception.GlobalExceptionHandler;
import com.banco.co.exception.support.ErrorResponseFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Task 5.2 — AccountAdminControllerTest targeting hexagonal admin adapter.
 * Uses standalone MockMvc. Mocks IAccountUseCase.
 *
 * RED: no test file existed after Phase 4 deleted the legacy admin controller tests.
 * GREEN: all scenarios pass against com.banco.co.account.adapter.in.rest.AccountAdminController.
 */
class AccountAdminControllerTest {

    private MockMvc mockMvc;
    private IAccountUseCase accountUseCase;

    private static final UUID ACCOUNT_ID = UUID.randomUUID();
    private static final String ADMIN_EMAIL = "admin@banco.co";

    private static final UsernamePasswordAuthenticationToken ADMIN_USER =
            new UsernamePasswordAuthenticationToken(ADMIN_EMAIL, "");

    @BeforeEach
    void setUp() {
        accountUseCase = mock(IAccountUseCase.class);

        mockMvc = MockMvcBuilders
                .standaloneSetup(new AccountAdminController(accountUseCase))
                .setControllerAdvice(new GlobalExceptionHandler(new ErrorResponseFactory()))
                .build();
    }

    // ── Helper ──────────────────────────────────────────────────────────────────

    private AccountResponseDto sampleResponse(AccountStatus status) {
        return new AccountResponseDto(
                "ACC-001",
                "1234567890",
                AccountType.SAVINGS,
                status,
                "CRC",
                new BigDecimal("5000.00"),
                new BigDecimal("5000.00"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                ADMIN_EMAIL,
                LocalDateTime.now(),
                LocalDateTime.now(),
                null
        );
    }

    // ── PUT /api/v1/admin/accounts/{id}/status ───────────────────────────────

    @Test
    void testUpdateStatus_ValidIdAndStatus_ReturnsOk() throws Exception {
        when(accountUseCase.updateAccountStatus(eq(ACCOUNT_ID), eq(AccountStatus.BLOCKED), anyString()))
                .thenReturn(sampleResponse(AccountStatus.BLOCKED));

        mockMvc.perform(put("/api/v1/admin/accounts/{id}/status", ACCOUNT_ID)
                        .param("status", "BLOCKED")
                        .principal(ADMIN_USER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("BLOCKED"));
    }

    @Test
    void testUpdateStatus_NotFound_Returns404() throws Exception {
        when(accountUseCase.updateAccountStatus(eq(ACCOUNT_ID), eq(AccountStatus.BLOCKED), anyString()))
                .thenThrow(new AccountNotFoundException(ACCOUNT_ID.toString()));

        mockMvc.perform(put("/api/v1/admin/accounts/{id}/status", ACCOUNT_ID)
                        .param("status", "BLOCKED")
                        .principal(ADMIN_USER))
                .andExpect(status().isNotFound());
    }

    @Test
    void testUpdateStatus_ActiveStatus_ReturnsOk() throws Exception {
        when(accountUseCase.updateAccountStatus(eq(ACCOUNT_ID), eq(AccountStatus.ACTIVE), anyString()))
                .thenReturn(sampleResponse(AccountStatus.ACTIVE));

        mockMvc.perform(put("/api/v1/admin/accounts/{id}/status", ACCOUNT_ID)
                        .param("status", "ACTIVE")
                        .principal(ADMIN_USER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    // ── POST /api/v1/admin/accounts/{id}/close ───────────────────────────────

    @Test
    void testCloseByAdmin_ValidId_Returns204() throws Exception {
        doNothing().when(accountUseCase).closeAccountByAdmin(ACCOUNT_ID, ADMIN_EMAIL);

        mockMvc.perform(post("/api/v1/admin/accounts/{id}/close", ACCOUNT_ID)
                        .principal(ADMIN_USER))
                .andExpect(status().isNoContent());
    }

    @Test
    void testCloseByAdmin_NotFound_Returns404() throws Exception {
        doThrow(new AccountNotFoundException(ACCOUNT_ID.toString()))
                .when(accountUseCase).closeAccountByAdmin(eq(ACCOUNT_ID), anyString());

        mockMvc.perform(post("/api/v1/admin/accounts/{id}/close", ACCOUNT_ID)
                        .principal(ADMIN_USER))
                .andExpect(status().isNotFound());
    }
}
