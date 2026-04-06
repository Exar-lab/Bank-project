package com.banco.co.account.controller;

import com.banco.co.account.dto.AccountResponseDto;
import com.banco.co.account.enums.AccountStatus;
import com.banco.co.account.enums.AccountType;
import com.banco.co.account.service.IAccountService;
import com.banco.co.exception.GlobalExceptionHandler;
import com.banco.co.exception.support.ErrorResponseFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AccountAdminControllerTest {

    private MockMvc mockMvc;
    private IAccountService accountService;

    private static final UsernamePasswordAuthenticationToken ADMIN =
            new UsernamePasswordAuthenticationToken("admin@banco.co", "");

    @BeforeEach
    void setUp() {
        accountService = mock(IAccountService.class);

        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders
                .standaloneSetup(new AccountAdminController(accountService))
                .setControllerAdvice(new GlobalExceptionHandler(new ErrorResponseFactory()))
                .setValidator(validator)
                .build();
    }

    @Test
    void testUpdateStatus_WhenRequestIsValid_Returns200() throws Exception {
        when(accountService.updateAccountStatus(any(UUID.class), any(AccountStatus.class), anyString()))
                .thenReturn(sampleResponse());

        mockMvc.perform(put("/api/v1/admin/accounts/{id}/status", UUID.randomUUID())
                        .param("status", "ACTIVE")
                        .principal(ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountCode").value("ACC-001"));
    }

    @Test
    void testCloseByAdmin_WhenRequestIsValid_Returns204() throws Exception {
        doNothing().when(accountService).closeAccountByAdmin(any(UUID.class), anyString());

        mockMvc.perform(post("/api/v1/admin/accounts/{id}/close", UUID.randomUUID())
                        .principal(ADMIN))
                .andExpect(status().isNoContent());
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
