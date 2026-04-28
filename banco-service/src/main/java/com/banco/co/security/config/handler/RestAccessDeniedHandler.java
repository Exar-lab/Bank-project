package com.banco.co.security.config.handler;

import com.banco.co.exception.ErrorResponseDto;
import com.banco.co.exception.support.ErrorResponseFactory;
import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;

@Component
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;
    private final ErrorResponseFactory errorResponseFactory;

    public RestAccessDeniedHandler(ObjectMapper objectMapper, ErrorResponseFactory errorResponseFactory) {
        this.objectMapper = objectMapper;
        this.errorResponseFactory = errorResponseFactory;
    }

    @Override
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        ErrorResponseDto body = errorResponseFactory.forbidden(
                "You do not have permission to access this resource",
                Map.of("path", request.getRequestURI())
        );

        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
