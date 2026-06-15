package com.banco.co.account.adapter.in.rest;

import com.banco.co.account.domain.port.in.IAccountUseCase;
import com.banco.co.account.dto.AccountRequestDto;
import com.banco.co.account.dto.AccountResponseDto;
import com.banco.co.account.dto.AccountUpdateDto;
import com.banco.co.account.enums.AccountStatus;
import com.banco.co.account.enums.AccountType;
import com.banco.co.account.exception.account.AccountNotFoundException;
import com.banco.co.exception.GlobalExceptionHandler;
import com.banco.co.exception.support.ErrorResponseFactory;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Task 5.1 — AccountControllerTest targeting hexagonal adapter.
 * Uses standalone MockMvc (no Spring context) to keep the slice fast.
 * Mocks IAccountUseCase — the single inbound port.
 *
 * RED: no test file existed after Phase 4 deleted the legacy controller tests.
 * GREEN: all scenarios pass against com.banco.co.account.adapter.in.rest.AccountController.
 */
class AccountControllerTest {

    private MockMvc mockMvc;
    private IAccountUseCase accountUseCase;
    private ObjectMapper objectMapper;

    private static final UUID ACCOUNT_ID = UUID.randomUUID();
    private static final String ACCOUNT_CODE = "ACC-001";
    private static final String USER_EMAIL = "owner@banco.co";

    private static final UsernamePasswordAuthenticationToken AUTHENTICATED_USER =
            new UsernamePasswordAuthenticationToken(USER_EMAIL, "");

    @BeforeEach
    void setUp() {
        accountUseCase = mock(IAccountUseCase.class);

        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders
                .standaloneSetup(new AccountController(accountUseCase))
                .setControllerAdvice(new GlobalExceptionHandler(new ErrorResponseFactory()))
                .setValidator(validator)
                .build();

        objectMapper = new ObjectMapper();
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
                USER_EMAIL,
                LocalDateTime.now(),
                LocalDateTime.now(),
                null
        );
    }

    // ── GET /api/v1/accounts ─────────────────────────────────────────────────

    @Test
    void testGetMyAccounts_ValidUser_ReturnsOk() throws Exception {
        when(accountUseCase.getMyAccounts(USER_EMAIL)).thenReturn(List.of(sampleResponse()));

        mockMvc.perform(get("/api/v1/accounts").principal(AUTHENTICATED_USER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].accountCode").value(ACCOUNT_CODE));
    }

    @Test
    void testGetMyAccounts_EmptyList_ReturnsOkWithEmptyArray() throws Exception {
        when(accountUseCase.getMyAccounts(USER_EMAIL)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/accounts").principal(AUTHENTICATED_USER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    // ── GET /api/v1/accounts/{id} ─────────────────────────────────────────────

    @Test
    void testGetAccount_ValidId_ReturnsOk() throws Exception {
        when(accountUseCase.getAccount(ACCOUNT_ID, USER_EMAIL)).thenReturn(sampleResponse());

        mockMvc.perform(get("/api/v1/accounts/{id}", ACCOUNT_ID).principal(AUTHENTICATED_USER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountCode").value(ACCOUNT_CODE));
    }

    @Test
    void testGetAccount_NotFound_Returns404() throws Exception {
        UUID unknownId = UUID.randomUUID();
        when(accountUseCase.getAccount(eq(unknownId), anyString()))
                .thenThrow(new AccountNotFoundException(unknownId.toString()));

        mockMvc.perform(get("/api/v1/accounts/{id}", unknownId).principal(AUTHENTICATED_USER))
                .andExpect(status().isNotFound());
    }

    // ── GET /api/v1/accounts/{id}/balance ───────────────────────────────────

    @Test
    void testGetUnassignedBalance_ValidId_ReturnsOk() throws Exception {
        when(accountUseCase.getAccount(ACCOUNT_ID, USER_EMAIL)).thenReturn(sampleResponse());
        when(accountUseCase.getUnassignedBalance(ACCOUNT_ID)).thenReturn(new BigDecimal("3000.00"));

        mockMvc.perform(get("/api/v1/accounts/{id}/balance", ACCOUNT_ID).principal(AUTHENTICATED_USER))
                .andExpect(status().isOk());
    }

    // ── POST /api/v1/accounts ─────────────────────────────────────────────────

    @Test
    void testCreateAccount_ValidRequest_Returns201() throws Exception {
        AccountRequestDto dto = new AccountRequestDto(
                AccountType.SAVINGS, "CRC", BigDecimal.ZERO, BigDecimal.ZERO, "123456789"
        );
        when(accountUseCase.createAccount(any(AccountRequestDto.class), anyString()))
                .thenReturn(sampleResponse());

        mockMvc.perform(post("/api/v1/accounts")
                        .principal(AUTHENTICATED_USER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accountCode").value(ACCOUNT_CODE));
    }

    @Test
    void testCreateAccount_MissingAccountType_Returns400() throws Exception {
        String invalidJson = """
                { "currency": "CRC", "overdraftLimit": 0, "interestRate": 0, "documentNumber": "123456789" }
                """;

        mockMvc.perform(post("/api/v1/accounts")
                        .principal(AUTHENTICATED_USER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testCreateAccount_InvalidCurrencyFormat_Returns400() throws Exception {
        String invalidJson = """
                { "accountType": "SAVINGS", "currency": "cr", "overdraftLimit": 0, "interestRate": 0, "documentNumber": "123456789" }
                """;

        mockMvc.perform(post("/api/v1/accounts")
                        .principal(AUTHENTICATED_USER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest());
    }

    // ── PUT /api/v1/accounts/{code} ──────────────────────────────────────────

    @Test
    void testUpdateAccount_ValidRequest_ReturnsOk() throws Exception {
        AccountUpdateDto dto = new AccountUpdateDto(BigDecimal.ZERO, BigDecimal.ZERO, "USD");
        when(accountUseCase.updateAccount(eq(ACCOUNT_CODE), any(AccountUpdateDto.class), anyString()))
                .thenReturn(sampleResponse());

        mockMvc.perform(put("/api/v1/accounts/{code}", ACCOUNT_CODE)
                        .principal(AUTHENTICATED_USER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountCode").value(ACCOUNT_CODE));
    }

    // ── DELETE /api/v1/accounts/{id} ─────────────────────────────────────────

    @Test
    void testCloseAccount_ValidId_Returns204() throws Exception {
        doNothing().when(accountUseCase).closeAccount(ACCOUNT_ID, USER_EMAIL);

        mockMvc.perform(delete("/api/v1/accounts/{id}", ACCOUNT_ID).principal(AUTHENTICATED_USER))
                .andExpect(status().isNoContent());
    }

    @Test
    void testCloseAccount_NotFound_Returns404() throws Exception {
        doThrow(new AccountNotFoundException(ACCOUNT_ID.toString()))
                .when(accountUseCase).closeAccount(eq(ACCOUNT_ID), anyString());

        mockMvc.perform(delete("/api/v1/accounts/{id}", ACCOUNT_ID).principal(AUTHENTICATED_USER))
                .andExpect(status().isNotFound());
    }
}
