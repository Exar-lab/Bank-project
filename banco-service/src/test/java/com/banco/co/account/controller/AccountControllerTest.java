package com.banco.co.account.controller;

import com.banco.co.account.dto.AccountRequestDto;
import com.banco.co.account.dto.AccountResponseDto;
import com.banco.co.account.enums.AccountStatus;
import com.banco.co.account.enums.AccountType;
import com.banco.co.account.service.IAccountService;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AccountControllerTest {

    private MockMvc mockMvc;
    private IAccountService accountService;
    private ObjectMapper objectMapper;

    private static final UsernamePasswordAuthenticationToken USER =
            new UsernamePasswordAuthenticationToken("customer@banco.co", "");

    @BeforeEach
    void setUp() {
        accountService = mock(IAccountService.class);

        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders
                .standaloneSetup(new AccountController(accountService))
                .setControllerAdvice(new GlobalExceptionHandler(new ErrorResponseFactory()))
                .setValidator(validator)
                .build();

        objectMapper = new ObjectMapper();
    }

    @Test
    void testGetMyAccounts_WhenServiceReturnsData_Returns200() throws Exception {
        when(accountService.getMyAccounts(anyString())).thenReturn(List.of(sampleResponse()));

        mockMvc.perform(get("/api/v1/accounts").principal(USER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].accountCode").value("ACC-001"));
    }

    @Test
    void testCreateAccount_WhenRequestIsValid_Returns201() throws Exception {
        AccountRequestDto dto = new AccountRequestDto(
                AccountType.CHECKING,
                "USD",
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                "12345678"
        );
        when(accountService.createAccount(any(AccountRequestDto.class), anyString())).thenReturn(sampleResponse());

        mockMvc.perform(post("/api/v1/accounts")
                        .principal(USER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accountCode").value("ACC-001"));
    }

    @Test
    void testCreateAccount_WhenRequestIsInvalid_Returns400() throws Exception {
        String invalidJson = """
                {
                  "currency": "US",
                  "documentNumber": ""
                }
                """;

        mockMvc.perform(post("/api/v1/accounts")
                        .principal(USER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
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
