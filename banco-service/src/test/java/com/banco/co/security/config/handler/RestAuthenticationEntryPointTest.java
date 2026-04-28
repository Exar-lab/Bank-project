package com.banco.co.security.config.handler;

import com.banco.co.exception.support.ErrorResponseFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RestAuthenticationEntryPointTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ErrorResponseFactory errorResponseFactory = new ErrorResponseFactory();

    @Test
    void testCommence_WhenUnauthorized_Returns401WithErrorResponseDtoContract() throws Exception {
        RestAuthenticationEntryPoint entryPoint = new RestAuthenticationEntryPoint(objectMapper, errorResponseFactory);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/transactions");

        MockHttpServletResponse response = new MockHttpServletResponse();

        entryPoint.commence(request, response, new BadCredentialsException("bad credentials"));

        JsonNode body = objectMapper.readTree(response.getContentAsString());
        List<String> rootFields = new ArrayList<>(body.propertyNames());

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).isEqualTo("application/json");
        assertThat(rootFields).containsExactlyInAnyOrder("errorCode", "message", "details", "timestamp");
        assertThat(body.has("code")).isFalse();
        assertThat(body.get("errorCode").asText()).isEqualTo("UNAUTHORIZED");
        assertThat(body.get("message").asText()).isEqualTo("Authentication is required to access this resource");
        assertThat(body.get("details").get("path").asText()).isEqualTo("/api/v1/transactions");
        assertThat(body.hasNonNull("timestamp")).isTrue();
    }
}
