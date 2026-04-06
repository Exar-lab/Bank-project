package com.banco.co.envelope.controller;

import com.banco.co.envelope.dto.EnvelopeResponseDto;
import com.banco.co.envelope.enums.EnvelopeStatus;
import com.banco.co.envelope.enums.EnvelopeType;
import com.banco.co.envelope.service.IEnvelopeService;
import com.banco.co.exception.GlobalExceptionHandler;
import com.banco.co.exception.support.ErrorResponseFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;

class EnvelopeControllerTest {

    private MockMvc mockMvc;
    private IEnvelopeService envelopeService;
    private ObjectMapper objectMapper;

    private static final UsernamePasswordAuthenticationToken USER =
            new UsernamePasswordAuthenticationToken("customer@banco.co", "");

    @BeforeEach
    void setUp() {
        envelopeService = mock(IEnvelopeService.class);

        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders
                .standaloneSetup(new EnvelopeController(envelopeService))
                .setControllerAdvice(new GlobalExceptionHandler(new ErrorResponseFactory()))
                .setValidator(validator)
                .build();

        objectMapper = new ObjectMapper();
    }

    @Test
    void testGetMyEnvelopes_WhenServiceReturnsData_Returns200() throws Exception {
        when(envelopeService.getMyEnvelopes(anyString())).thenReturn(List.of(sampleResponse()));

        mockMvc.perform(get("/api/v1/envelopes").principal(USER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Emergency Fund"));
    }

    @Test
    void testCreate_WhenRequestIsValid_Returns201() throws Exception {
        String validJson = """
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
                        .principal(USER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Emergency Fund"));
    }

    @Test
    void testCreate_WhenRequestIsInvalid_Returns400() throws Exception {
        String invalidJson = """
                {
                  "description": "invalid",
                  "targetAmount": -5
                }
                """;

        mockMvc.perform(post("/api/v1/envelopes")
                        .principal(USER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(content().string(not(containsString("Exception"))))
                .andExpect(content().string(not(containsString("org.springframework"))));
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
