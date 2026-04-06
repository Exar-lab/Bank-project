package com.banco.co.user.controller;

import com.banco.co.exception.GlobalExceptionHandler;
import com.banco.co.exception.support.ErrorResponseFactory;
import com.banco.co.user.dto.customer.CustomerResponseDto;
import com.banco.co.user.dto.employee.EmployeeResponseDto;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UserAdminControllerTest {

    private MockMvc mockMvc;
    private IUserService userService;
    private ObjectMapper objectMapper;

    private static final UsernamePasswordAuthenticationToken ADMIN =
            new UsernamePasswordAuthenticationToken("admin@banco.co", "");

    @BeforeEach
    void setUp() {
        userService = mock(IUserService.class);

        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders
                .standaloneSetup(new UserAdminController(userService))
                .setControllerAdvice(new GlobalExceptionHandler(new ErrorResponseFactory()))
                .setValidator(validator)
                .build();

        objectMapper = new ObjectMapper();
    }

    @Test
    void testGetById_WhenRequestIsValid_Returns200() throws Exception {
        when(userService.getUserById(any(UUID.class), anyString())).thenReturn(sampleCustomerResponse());

        mockMvc.perform(get("/api/v1/admin/users/{id}", UUID.randomUUID())
                        .principal(ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("user@banco.co"));
    }

    @Test
    void testSuspend_WhenReasonProvided_Returns204() throws Exception {
        doNothing().when(userService).suspendUser(any(UUID.class), anyString(), anyString());

        mockMvc.perform(put("/api/v1/admin/users/{id}/suspend", UUID.randomUUID())
                        .param("reason", "policy")
                        .principal(ADMIN))
                .andExpect(status().isNoContent());
    }

    @Test
    void testSuspend_WhenReasonMissing_Returns400() throws Exception {
        mockMvc.perform(put("/api/v1/admin/users/{id}/suspend", UUID.randomUUID())
                        .principal(ADMIN))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }

    @Test
    void testCreateEmployee_WhenRequestIsValid_Returns201() throws Exception {
        when(userService.createUserByEmployee(anyString(), any())).thenReturn(sampleEmployeeResponse());

        String payload = """
                {
                  "fistName": "Ana",
                  "lastName": "Admin",
                  "email": "ana.admin@banco.co",
                  "password": "Passw0rd!",
                  "documentNumber": "11122233",
                  "documentType": "CEDULA",
                  "birthDate": "1992-03-10",
                  "phoneNumber": "+573001112244",
                  "address": "Street 1",
                  "role": "TELLER"
                }
                """;

        mockMvc.perform(post("/api/v1/admin/users/employees")
                        .principal(ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("employee@banco.co"));
    }

    private CustomerResponseDto sampleCustomerResponse() {
        return new CustomerResponseDto(
                UUID.randomUUID(),
                "USR-001",
                "User",
                "Target",
                "user.target",
                "user@banco.co",
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

    private EmployeeResponseDto sampleEmployeeResponse() {
        return new EmployeeResponseDto(
                UUID.randomUUID(),
                "EMP-001",
                "Jane",
                "Admin",
                "jane.admin",
                "employee@banco.co",
                "98765432",
                DocumentType.CEDULA,
                LocalDate.of(1991, 1, 1),
                "+573009998877",
                "Admin Street",
                UserStatus.ACTIVE,
                KycStatus.PENDING,
                LocalDateTime.now(),
                LocalDateTime.now()
        );
    }
}
