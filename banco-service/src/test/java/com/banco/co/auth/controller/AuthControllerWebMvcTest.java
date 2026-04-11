package com.banco.co.auth.controller;

import com.banco.co.auth.dto.TokenPairResponseDto;
import com.banco.co.auth.service.AuthService;
import com.banco.co.exception.GlobalExceptionHandler;
import com.banco.co.exception.authentication.AccountLockedException;
import com.banco.co.exception.authentication.InvalidCredentialsException;
import com.banco.co.exception.authentication.InvalidTokenException;
import com.banco.co.exception.support.ErrorResponseFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthControllerWebMvcTest {

    private MockMvc mockMvc;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = mock(AuthService.class);

        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders
                .standaloneSetup(new AuthController(authService))
                .setControllerAdvice(new GlobalExceptionHandler(new ErrorResponseFactory()))
                .setValidator(validator)
                .build();
    }

    @Test
    void testLogin_WithValidCredentials_Returns200() throws Exception {
        when(authService.login(any())).thenReturn(new TokenPairResponseDto("access-token", "refresh-token", "Bearer", 900L));

        String payload = """
                {
                  "email": "user@banco.co",
                  "password": "Passw0rd!"
                }
                """;

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access-token"))
                .andExpect(jsonPath("$.refreshToken").value("refresh-token"));
    }

    @Test
    void testLogin_WithInvalidCredentials_Returns401WithErrorBody() throws Exception {
        when(authService.login(any())).thenThrow(new InvalidCredentialsException("user@banco.co"));

        String payload = """
                {
                  "email": "user@banco.co",
                  "password": "bad"
                }
                """;

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.details").isMap());
    }

    @Test
    void testLogin_WithLockedAccount_Returns403() throws Exception {
        when(authService.login(any())).thenThrow(new AccountLockedException("locked@banco.co"));

        String payload = """
                {
                  "email": "locked@banco.co",
                  "password": "Passw0rd!"
                }
                """;

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCOUNT_LOCKED"));
    }

    @Test
    void testRefresh_WithValidToken_Returns200() throws Exception {
        when(authService.refresh(any())).thenReturn(new TokenPairResponseDto("new-access", "new-refresh", "Bearer", 900L));

        String payload = """
                {
                  "refreshToken": "refresh-token"
                }
                """;

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("new-access"))
                .andExpect(jsonPath("$.refreshToken").value("new-refresh"));
    }

    @Test
    void testRefresh_WhenReuseDetected_Returns401() throws Exception {
        when(authService.refresh(any())).thenThrow(new InvalidTokenException("Refresh token reuse detected"));

        String payload = """
                {
                  "refreshToken": "reused-token"
                }
                """;

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INVALID_TOKEN"));
    }

    @Test
    void testRefresh_WhenTokenExpired_Returns401WithTokenExpiredCode() throws Exception {
        when(authService.refresh(any())).thenThrow(new com.banco.co.exception.authentication.TokenExpiredException("Refresh token has expired"));

        String payload = """
                {
                  "refreshToken": "expired-token"
                }
                """;

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("TOKEN_EXPIRED"));
    }

    @Test
    void testRefresh_WhenTokenCryptographicallyInvalid_Returns401WithInvalidTokenCode() throws Exception {
        when(authService.refresh(any())).thenThrow(new InvalidTokenException("Invalid refresh token"));

        String payload = """
                {
                  "refreshToken": "invalid-token"
                }
                """;

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INVALID_TOKEN"));
    }

    @Test
    void testLogout_WithAnyToken_Returns204() throws Exception {
        doNothing().when(authService).logout(any());

        String payload = """
                {
                  "refreshToken": "refresh-token"
                }
                """;

        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isNoContent());
    }
}
