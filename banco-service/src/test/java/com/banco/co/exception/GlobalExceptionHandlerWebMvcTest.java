package com.banco.co.exception;

import com.banco.co.exception.support.ErrorResponseFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindException;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.annotation.Validated;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;

class GlobalExceptionHandlerWebMvcTest {

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        this.mockMvc = MockMvcBuilders
                .standaloneSetup(new TestController())
                .setControllerAdvice(new GlobalExceptionHandler(new ErrorResponseFactory()))
                .setValidator(validator)
                .build();
    }

    @Test
    void testCreate_WithInvalidBody_ReturnsValidationFailed() throws Exception {
        mockMvc.perform(post("/test/validate-body")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateRequest(""))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value("Request validation failed"))
                .andExpect(jsonPath("$.details.name").exists())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(content().string(not(containsString("Exception"))))
                .andExpect(content().string(not(containsString("org.springframework"))));
    }

    @Test
    void testValidateParam_WithInvalidRequestParam_ReturnsValidationFailed() throws Exception {
        mockMvc.perform(get("/test/validate-param").param("page", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value("Request validation failed"))
                .andExpect(jsonPath("$.details.error-1").value("page must be greater than or equal to 1"));
    }

    @Test
    void testBind_WithBindingError_ReturnsValidationFailed() throws Exception {
        mockMvc.perform(get("/test/bind"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details.field").value("invalid value"));
    }

    @Test
    void testIllegalArgument_WithInvalidInput_ReturnsValidationFailed() throws Exception {
        mockMvc.perform(get("/test/illegal-argument"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details.error-1").value("invalid argument"));
    }

    @Test
    void testConstraintViolation_WithConstraintViolationException_ReturnsValidationFailed() throws Exception {
        mockMvc.perform(get("/test/constraint-violation"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value("Request validation failed"))
                .andExpect(jsonPath("$.details").isMap());
    }

    @Test
    void testMissingRequestParameter_WhenRequiredParamAbsent_ReturnsValidationFailed() throws Exception {
        mockMvc.perform(get("/test/missing-param"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value("Request validation failed"))
                .andExpect(jsonPath("$.details.error-1").value("Required request parameter 'requiredValue' is missing"));
    }

    @Test
    void testHandleBankingException_WithDomainException_ReturnsExceptionStatusAndErrorCode() throws Exception {
        mockMvc.perform(get("/test/domain-exception"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_RULE_VIOLATION"))
                .andExpect(jsonPath("$.message").value("business rule broken"))
                .andExpect(jsonPath("$.details.resource").value("account"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.code").doesNotExist());
    }

    @Test
    void testLegacyConsumerParser_WhenDomainExceptionSerialized_ThenParsesCanonicalContractFields() throws Exception {
        String responseBody = mockMvc.perform(get("/test/domain-exception"))
                .andExpect(status().isConflict())
                .andReturn()
                .getResponse()
                .getContentAsString();

        LegacyConsumerErrorPayload payload = objectMapper.readValue(responseBody, LegacyConsumerErrorPayload.class);

        assertThat(payload.errorCode()).isEqualTo("BUSINESS_RULE_VIOLATION");
        assertThat(payload.message()).isEqualTo("business rule broken");
        assertThat(payload.details()).containsEntry("resource", "account");
        assertThat(payload.timestamp()).isNotNull();

        java.util.Iterator<String> fieldNames = objectMapper.readTree(responseBody).fieldNames();
        Set<String> rootFields = new java.util.HashSet<>();
        fieldNames.forEachRemaining(rootFields::add);

        assertThat(rootFields).containsExactlyInAnyOrder("errorCode", "message", "details", "timestamp");
        assertThat(rootFields).doesNotContain("code");
    }

    @Test
    void testUnhandled_WithUnexpectedException_ReturnsInternalErrorWithoutStackTrace() throws Exception {
        mockMvc.perform(get("/test/unhandled"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.errorCode").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("An unexpected error occurred. Please try again later."))
                .andExpect(jsonPath("$.details").isMap())
                .andExpect(content().string(not(containsString("java.lang.RuntimeException"))))
                .andExpect(content().string(not(containsString("boom"))));
    }

    @RestController
    @Validated
    @RequestMapping("/test")
    static class TestController {

        @PostMapping("/validate-body")
        String create(@Valid @RequestBody CreateRequest request) {
            return request.name();
        }

        @GetMapping("/validate-param")
        String validateParam(@RequestParam int page) {
            if (page < 1) {
                throw new IllegalArgumentException("page must be greater than or equal to 1");
            }

            return String.valueOf(page);
        }

        @GetMapping("/bind")
        String bind() throws BindException {
            BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new BindTarget(), "request");
            bindingResult.rejectValue("field", "invalid", "invalid value");
            throw new BindException(bindingResult);
        }

        @GetMapping("/illegal-argument")
        String illegalArgument() {
            throw new IllegalArgumentException("invalid argument");
        }

        @GetMapping("/constraint-violation")
        String constraintViolation() {
            java.util.Set<jakarta.validation.ConstraintViolation<?>> violations = java.util.Collections.emptySet();
            throw new ConstraintViolationException("constraint failed", violations);
        }

        @GetMapping("/missing-param")
        String missingParam(@RequestParam String requiredValue) {
            return requiredValue;
        }

        @GetMapping("/domain-exception")
        String domainException() {
            throw new TestBankingException("business rule broken").addMetadata("resource", "account");
        }

        @GetMapping("/unhandled")
        String unhandled() {
            throw new RuntimeException("boom");
        }
    }

    static final class TestBankingException extends BankingException {

        private TestBankingException(String message) {
            super(message, "BUSINESS_RULE_VIOLATION", HttpStatus.CONFLICT);
        }
    }

    static final class BindTarget {
        private String field;

        public String getField() {
            return field;
        }

        public void setField(String field) {
            this.field = field;
        }
    }

    record CreateRequest(@NotBlank String name) {
    }

    record LegacyConsumerErrorPayload(
            String errorCode,
            String message,
            Map<String, Object> details,
            Object timestamp
    ) {
    }
}
