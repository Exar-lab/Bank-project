package com.banco.co.security.config.filter;

import com.auth0.jwt.interfaces.DecodedJWT;
import com.banco.co.exception.authentication.InvalidTokenException;
import com.banco.co.utils.JwtUtils;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class JwtTokenValidatorTest {

    private JwtUtils jwtUtils;
    private JwtTokenValidator jwtTokenValidator;

    @BeforeEach
    void setUp() {
        jwtUtils = mock(JwtUtils.class);
        jwtTokenValidator = new JwtTokenValidator(jwtUtils);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void testDoFilterInternal_WithoutAuthorizationHeader_ContinuesWithoutAuthentication() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        jwtTokenValidator.doFilter(request, response, filterChain);

        verify(filterChain, times(1)).doFilter(request, response);
        verify(jwtUtils, never()).validateAccessToken(anyString());
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void testDoFilterInternal_WithValidBearerToken_SetsAuthenticationWithRolesAndScopes() throws Exception {
        String token = "valid-token";
        DecodedJWT decodedJWT = mock(DecodedJWT.class);

        when(jwtUtils.validateAccessToken(token)).thenReturn(decodedJWT);
        when(jwtUtils.getUsername(decodedJWT)).thenReturn("usuario@banco.co");
        when(jwtUtils.getRoles(decodedJWT)).thenReturn(List.of("ROLE_ADMIN"));
        when(jwtUtils.getScopes(decodedJWT)).thenReturn(List.of("transaction:create"));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        jwtTokenValidator.doFilter(request, response, filterChain);

        verify(filterChain, times(1)).doFilter(request, response);
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        assertThat(authentication).isNotNull();
        assertThat(authentication.getName()).isEqualTo("usuario@banco.co");
        assertThat(authentication.getAuthorities())
                .extracting("authority")
                .containsExactlyInAnyOrder("ROLE_ADMIN", "transaction:create");
    }

    @Test
    void testDoFilterInternal_WithInvalidBearerToken_DoesNotSetAuthenticationAndContinues() throws Exception {
        String token = "invalid-token";
        when(jwtUtils.validateAccessToken(token)).thenThrow(new InvalidTokenException("invalid token"));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        jwtTokenValidator.doFilter(request, response, filterChain);

        verify(filterChain, times(1)).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void testDoFilterInternal_WithValidTokenAndNullUsername_DoesNotSetAuthenticationAndContinues() throws Exception {
        String token = "valid-token";
        DecodedJWT decodedJWT = mock(DecodedJWT.class);

        when(jwtUtils.validateAccessToken(token)).thenReturn(decodedJWT);
        when(jwtUtils.getUsername(decodedJWT)).thenReturn(null);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        jwtTokenValidator.doFilter(request, response, filterChain);

        verify(filterChain, times(1)).doFilter(request, response);
        verify(jwtUtils, never()).getRoles(decodedJWT);
        verify(jwtUtils, never()).getScopes(decodedJWT);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void testDoFilterInternal_WithValidTokenAndBlankUsername_DoesNotSetAuthenticationAndContinues() throws Exception {
        String token = "valid-token";
        DecodedJWT decodedJWT = mock(DecodedJWT.class);

        when(jwtUtils.validateAccessToken(token)).thenReturn(decodedJWT);
        when(jwtUtils.getUsername(decodedJWT)).thenReturn("   ");

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        jwtTokenValidator.doFilter(request, response, filterChain);

        verify(filterChain, times(1)).doFilter(request, response);
        verify(jwtUtils, never()).getRoles(decodedJWT);
        verify(jwtUtils, never()).getScopes(decodedJWT);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}
