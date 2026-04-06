package com.banco.co.envelope.controller;

import com.banco.co.envelope.dto.EnvelopeResponseDto;
import com.banco.co.envelope.enums.EnvelopeStatus;
import com.banco.co.envelope.enums.EnvelopeType;
import com.banco.co.envelope.service.IEnvelopeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = EnvelopeController.class)
@ContextConfiguration(classes = {
        EnvelopeControllerSecuritySliceWebMvcTest.TestApplication.class,
        EnvelopeController.class,
        EnvelopeControllerSecuritySliceWebMvcTest.MethodSecurityTestConfig.class
})
@Import({
        EnvelopeControllerSecuritySliceWebMvcTest.MethodSecurityTestConfig.class
})
class EnvelopeControllerSecuritySliceWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IEnvelopeService envelopeService;

    @Test
    @WithMockUser(username = "customer@banco.co", authorities = "SCOPE_envelope:read")
    void testGetMyEnvelopes_WithReadScope_Returns200() throws Exception {
        when(envelopeService.getMyEnvelopes(anyString())).thenReturn(List.of(sampleResponse()));

        mockMvc.perform(get("/api/v1/envelopes"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "customer@banco.co", authorities = "envelope:read")
    void testGetMyEnvelopes_WithLegacyReadAuthority_Returns200() throws Exception {
        when(envelopeService.getMyEnvelopes(anyString())).thenReturn(List.of(sampleResponse()));

        mockMvc.perform(get("/api/v1/envelopes"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "customer@banco.co", authorities = "SCOPE_envelope:write")
    void testCreate_WithWriteScope_Returns201() throws Exception {
        String validBody = """
                {
                  "name": "Emergency Fund",
                  "description": "Rainy day",
                  "accountCode": "ACC-001",
                  "envelopeType": "EMERGENCY",
                  "targetAmount": 5000
                }
                """;

        when(envelopeService.create(any(), anyString())).thenReturn(sampleResponse());

        mockMvc.perform(post("/api/v1/envelopes")
                        .contentType("application/json")
                        .content(validBody))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(username = "customer@banco.co", authorities = "SCOPE_envelope:read")
    void testCreate_WithReadScopeOnly_Returns403() throws Exception {
        String validBody = """
                {
                  "name": "Emergency Fund",
                  "description": "Rainy day",
                  "accountCode": "ACC-001",
                  "envelopeType": "EMERGENCY",
                  "targetAmount": 5000
                }
                """;

        mockMvc.perform(post("/api/v1/envelopes")
                        .contentType("application/json")
                        .content(validBody))
                .andExpect(status().isForbidden());
    }

    @Test
    void testGetMyEnvelopes_WithoutAuthentication_Returns401() throws Exception {
        mockMvc.perform(get("/api/v1/envelopes"))
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

    private EnvelopeResponseDto sampleResponse() {
        return new EnvelopeResponseDto(
                "Emergency Fund",
                EnvelopeType.EMERGENCY,
                EnvelopeStatus.ACTIVE,
                LocalDate.now().plusMonths(6),
                BigDecimal.TEN,
                new BigDecimal("1000.00"),
                "💰",
                "#00FF00"
        );
    }
}
