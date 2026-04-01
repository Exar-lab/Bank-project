package com.banco.co.exception.support;

import com.banco.co.exception.ErrorResponseDto;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ErrorResponseFactoryTest {

    private final ErrorResponseFactory factory = new ErrorResponseFactory();

    @Test
    void testValidationFailed_WithDetails_ReturnsDtoWithValidationCode() {
        ErrorResponseDto response = factory.validationFailed("validation", Map.of("field", "invalid"));

        assertThat(response.errorCode()).isEqualTo("VALIDATION_FAILED");
        assertThat(response.message()).isEqualTo("validation");
        assertThat(response.details()).containsEntry("field", "invalid");
        assertThat(response.timestamp()).isNotNull();
    }

    @Test
    void testInternalError_WithNullDetails_ReturnsDtoWithEmptyDetails() {
        ErrorResponseDto response = factory.internalError("internal", null);

        assertThat(response.errorCode()).isEqualTo("INTERNAL_ERROR");
        assertThat(response.message()).isEqualTo("internal");
        assertThat(response.details()).isEmpty();
        assertThat(response.timestamp()).isNotNull();
    }

    @Test
    void testWithCode_WithDomainCode_ReturnsDtoWithProvidedCode() {
        ErrorResponseDto response = factory.withCode("ACCOUNT_NOT_FOUND", "not found", Map.of("id", "abc"));

        assertThat(response.errorCode()).isEqualTo("ACCOUNT_NOT_FOUND");
        assertThat(response.message()).isEqualTo("not found");
        assertThat(response.details()).containsEntry("id", "abc");
        assertThat(response.timestamp()).isNotNull();
    }
}
