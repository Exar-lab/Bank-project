package com.banco.co.user.controller;

import com.banco.co.exception.GlobalExceptionHandler;
import com.banco.co.exception.support.ErrorResponseFactory;
import com.banco.co.user.dto.customer.CustomerResponseDto;
import com.banco.co.user.enums.DocumentType;
import com.banco.co.user.enums.KycStatus;
import com.banco.co.user.enums.UserStatus;
import com.banco.co.user.service.user.IUserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UserControllerTest {

    private MockMvc mockMvc;
    private IUserService userService;
    private ObjectMapper objectMapper;

    private static final UsernamePasswordAuthenticationToken USER =
            new UsernamePasswordAuthenticationToken("john@banco.co", "");

    @BeforeEach
    void setUp() {
        userService = mock(IUserService.class);

        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders
                .standaloneSetup(new UserController(userService))
                .setControllerAdvice(new GlobalExceptionHandler(new ErrorResponseFactory()))
                .setValidator(validator)
                .build();

        objectMapper = new ObjectMapper();
    }

    @Test
    void testMe_WhenAuthenticated_Returns200() throws Exception {
        when(userService.findUserByEmail(anyString())).thenReturn(sampleResponse());

        mockMvc.perform(get("/api/v1/users/me").principal(USER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("john@banco.co"));
    }

    @Test
    void testUpdatePassword_WhenRequestIsValid_Returns204() throws Exception {
        String payload = """
                {
                  "currentPassword": "Passw0rd!",
                  "password": "Passw0rd!",
                  "confirmPassword": "Passw0rd!"
                }
                """;

        doNothing().when(userService).updatePassword(any(), anyString());

        mockMvc.perform(put("/api/v1/users/me/password")
                        .principal(USER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isNoContent());
    }

    @Test
    void testDeleteMe_WhenAuthenticated_Returns204() throws Exception {
        doNothing().when(userService).deleteUserByEmail(anyString());

        mockMvc.perform(delete("/api/v1/users/me").principal(USER))
                .andExpect(status().isNoContent());
    }

    @Test
    void testUpdateMe_WhenRequestIsInvalid_Returns400() throws Exception {
        String invalidPayload = """
                {
                  "username": "a"
                }
                """;

        mockMvc.perform(put("/api/v1/users/me")
                        .principal(USER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidPayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }

    private CustomerResponseDto sampleResponse() {
        return new CustomerResponseDto(
                UUID.randomUUID(),
                "USR-001",
                "John",
                "Doe",
                "john.doe",
                "john@banco.co",
                "12345678",
                DocumentType.CEDULA,
                LocalDate.of(1990, 1, 1),
                "+573001112233",
                "Street 123",
                UserStatus.ACTIVE,
                KycStatus.PENDING,
                LocalDateTime.now(),
                LocalDateTime.now()
        );
    }
}
