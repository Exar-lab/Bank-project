package com.banco.co.user.adapter.in.rest;

import com.banco.co.exception.GlobalExceptionHandler;
import com.banco.co.exception.support.ErrorResponseFactory;
import com.banco.co.user.domain.port.in.IUserUseCase;
import com.banco.co.user.dto.customer.CustomerResponseDto;
import com.banco.co.user.dto.employee.EmployeeResponseDto;
import com.banco.co.user.enums.DocumentType;
import com.banco.co.user.enums.KycStatus;
import com.banco.co.user.enums.UserStatus;
import tools.jackson.databind.ObjectMapper;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * UserAdminControllerTest targeting the hexagonal admin adapter.
 * Uses standalone MockMvc (no Spring context) to keep the slice fast.
 * Mocks IUserUseCase — the single inbound port.
 *
 * RED: no test file existed after the legacy admin controller and its test were deleted.
 * GREEN: all scenarios pass against com.banco.co.user.adapter.in.rest.UserAdminController.
 *
 * Covers all 6 admin endpoints, each carrying its own @PreAuthorize expression:
 * getById, updateByAdmin, suspend, activate, updateStatus, createEmployee.
 */
class UserAdminControllerTest {

    private MockMvc mockMvc;
    private IUserUseCase userUseCase;
    private ObjectMapper objectMapper;

    private static final UUID USER_ID = UUID.randomUUID();
    private static final String ADMIN_EMAIL = "admin@banco.co";

    private static final UsernamePasswordAuthenticationToken ADMIN =
            new UsernamePasswordAuthenticationToken(ADMIN_EMAIL, "");

    @BeforeEach
    void setUp() {
        userUseCase = mock(IUserUseCase.class);

        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders
                .standaloneSetup(new UserAdminController(userUseCase))
                .setControllerAdvice(new GlobalExceptionHandler(new ErrorResponseFactory()))
                .setValidator(validator)
                .build();

        objectMapper = new ObjectMapper();
    }

    // ── GET /api/v1/admin/users/{id} ─────────────────────────────────────────

    @Test
    void testGetById_WhenRequestIsValid_Returns200() throws Exception {
        when(userUseCase.getUserById(eq(USER_ID), eq(ADMIN_EMAIL))).thenReturn(sampleCustomerResponse());

        mockMvc.perform(get("/api/v1/admin/users/{id}", USER_ID)
                        .principal(ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("user@banco.co"));
    }

    // ── PUT /api/v1/admin/users/{id} ─────────────────────────────────────────

    @Test
    void testUpdateByAdmin_WhenRequestIsValid_Returns200() throws Exception {
        when(userUseCase.updateUserByAdmin(eq(USER_ID), any(), eq(ADMIN_EMAIL)))
                .thenReturn(sampleCustomerResponse());

        String payload = """
                {
                  "fistName": "Updated",
                  "lastName": "Name",
                  "phoneNumber": "+573001112233",
                  "address": "New Street 1",
                  "username": "updated.name"
                }
                """;

        mockMvc.perform(put("/api/v1/admin/users/{id}", USER_ID)
                        .principal(ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("user@banco.co"));
    }

    @Test
    void testUpdateByAdmin_WhenRequestIsInvalid_Returns400() throws Exception {
        String invalidPayload = """
                {
                  "fistName": "123"
                }
                """;

        mockMvc.perform(put("/api/v1/admin/users/{id}", USER_ID)
                        .principal(ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidPayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }

    // ── PUT /api/v1/admin/users/{id}/suspend ─────────────────────────────────

    @Test
    void testSuspend_WhenReasonProvided_Returns204() throws Exception {
        doNothing().when(userUseCase).suspendUser(eq(USER_ID), anyString(), eq(ADMIN_EMAIL));

        mockMvc.perform(put("/api/v1/admin/users/{id}/suspend", USER_ID)
                        .param("reason", "policy")
                        .principal(ADMIN))
                .andExpect(status().isNoContent());
    }

    @Test
    void testSuspend_WhenReasonMissing_Returns400() throws Exception {
        mockMvc.perform(put("/api/v1/admin/users/{id}/suspend", USER_ID)
                        .principal(ADMIN))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }

    // ── PUT /api/v1/admin/users/{id}/activate ────────────────────────────────

    @Test
    void testActivate_WhenRequestIsValid_Returns204() throws Exception {
        doNothing().when(userUseCase).activateUser(eq(USER_ID), eq(ADMIN_EMAIL));

        mockMvc.perform(put("/api/v1/admin/users/{id}/activate", USER_ID)
                        .principal(ADMIN))
                .andExpect(status().isNoContent());
    }

    // ── PUT /api/v1/admin/users/{id}/status ──────────────────────────────────

    @Test
    void testUpdateStatus_WhenRequestIsValid_Returns200() throws Exception {
        when(userUseCase.updateUserStatus(eq(USER_ID), eq(UserStatus.SUSPENDED), eq(ADMIN_EMAIL)))
                .thenReturn(sampleCustomerResponse());

        mockMvc.perform(put("/api/v1/admin/users/{id}/status", USER_ID)
                        .param("status", "SUSPENDED")
                        .principal(ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("user@banco.co"));
    }

    // ── POST /api/v1/admin/users/employees ───────────────────────────────────

    @Test
    void testCreateEmployee_WhenRequestIsValid_Returns201() throws Exception {
        when(userUseCase.createUserByEmployee(eq(ADMIN_EMAIL), any())).thenReturn(sampleEmployeeResponse());

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

    @Test
    void testCreateEmployee_WhenRequestIsInvalid_Returns400() throws Exception {
        String invalidPayload = """
                {
                  "fistName": "Ana"
                }
                """;

        mockMvc.perform(post("/api/v1/admin/users/employees")
                        .principal(ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidPayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }

    // ── Fixtures ──────────────────────────────────────────────────────────────

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
