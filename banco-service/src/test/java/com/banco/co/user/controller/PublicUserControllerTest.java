package com.banco.co.user.controller;

import com.banco.co.exception.GlobalExceptionHandler;
import com.banco.co.exception.support.ErrorResponseFactory;
import com.banco.co.user.dto.customer.CustomerResponseDto;
import com.banco.co.user.enums.DocumentType;
import com.banco.co.user.enums.KycStatus;
import com.banco.co.user.enums.UserStatus;
import com.banco.co.user.service.user.IUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PublicUserControllerTest {

    private MockMvc mockMvc;
    private IUserService userService;

    @BeforeEach
    void setUp() {
        userService = mock(IUserService.class);

        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders
                .standaloneSetup(new PublicUserController(userService))
                .setControllerAdvice(new GlobalExceptionHandler(new ErrorResponseFactory()))
                .setValidator(validator)
                .build();
    }

    @Test
    void testRegister_WhenRequestIsValid_Returns201() throws Exception {
        when(userService.createUser(any())).thenReturn(sampleResponse());

        String payload = """
                {
                  "fistName": "Juan",
                  "lastName": "Perez",
                  "email": "juan@banco.co",
                  "password": "Passw0rd!",
                  "documentNumber": "12345678",
                  "documentType": "CEDULA",
                  "birthDate": "1990-01-01",
                  "phoneNumber": "+573001112233",
                  "address": "Street 123",
                  "username": "juan.perez"
                }
                """;

        mockMvc.perform(post("/api/v1/public/users/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("john@banco.co"));
    }

    @Test
    void testRegister_WhenRequestIsInvalid_Returns400() throws Exception {
        String invalidPayload = """
                {
                  "email": "bad-email"
                }
                """;

        mockMvc.perform(post("/api/v1/public/users/register")
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
