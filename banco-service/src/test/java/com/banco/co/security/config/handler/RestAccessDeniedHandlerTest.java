package com.banco.co.security.config.handler;

import com.banco.co.exception.support.ErrorResponseFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RestAccessDeniedHandlerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ErrorResponseFactory errorResponseFactory = new ErrorResponseFactory();

    @Test
    void testHandle_WhenForbidden_Returns403WithErrorResponseDtoContract() throws Exception {
        RestAccessDeniedHandler deniedHandler = new RestAccessDeniedHandler(objectMapper, errorResponseFactory);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/admin/transactions");

        MockHttpServletResponse response = new MockHttpServletResponse();

        deniedHandler.handle(request, response, new AccessDeniedException("denied"));

        JsonNode body = objectMapper.readTree(response.getContentAsString());
        List<String> rootFields = new ArrayList<>(body.propertyNames());

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentType()).isEqualTo("application/json");
        assertThat(rootFields).containsExactlyInAnyOrder("errorCode", "message", "details", "timestamp");
        assertThat(body.has("code")).isFalse();
        assertThat(body.get("errorCode").asText()).isEqualTo("FORBIDDEN");
        assertThat(body.get("message").asText()).isEqualTo("You do not have permission to access this resource");
        assertThat(body.get("details").get("path").asText()).isEqualTo("/api/v1/admin/transactions");
        assertThat(body.hasNonNull("timestamp")).isTrue();
    }
}
