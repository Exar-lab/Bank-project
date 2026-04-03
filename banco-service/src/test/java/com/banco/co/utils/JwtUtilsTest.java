package com.banco.co.utils;

import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.banco.co.exception.authentication.InvalidTokenException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

class JwtUtilsTest {

    @Test
    void testValidateAccessToken_WithAccessTypeAndValidSubject_ReturnsDecodedToken() {
        JwtUtils jwtUtils = spy(new JwtUtils());
        DecodedJWT decodedJWT = mock(DecodedJWT.class);
        Claim typeClaim = mock(Claim.class);

        doReturn(decodedJWT).when(jwtUtils).validateToken("valid-access-token");
        when(decodedJWT.getClaim("type")).thenReturn(typeClaim);
        when(typeClaim.asString()).thenReturn("access");
        when(decodedJWT.getSubject()).thenReturn("usuario@banco.co");

        DecodedJWT result = jwtUtils.validateAccessToken("valid-access-token");

        assertThat(result).isSameAs(decodedJWT);
    }

    @Test
    void testValidateAccessToken_WithAccessTypeAndNullSubject_ThrowsInvalidTokenException() {
        JwtUtils jwtUtils = spy(new JwtUtils());
        DecodedJWT decodedJWT = mock(DecodedJWT.class);
        Claim typeClaim = mock(Claim.class);

        doReturn(decodedJWT).when(jwtUtils).validateToken("access-token-with-null-sub");
        when(decodedJWT.getClaim("type")).thenReturn(typeClaim);
        when(typeClaim.asString()).thenReturn("access");
        when(decodedJWT.getSubject()).thenReturn(null);

        assertThatThrownBy(() -> jwtUtils.validateAccessToken("access-token-with-null-sub"))
                .isInstanceOf(InvalidTokenException.class)
                .hasMessage("Access token subject is missing or blank");
    }

    @Test
    void testValidateAccessToken_WithAccessTypeAndBlankSubject_ThrowsInvalidTokenException() {
        JwtUtils jwtUtils = spy(new JwtUtils());
        DecodedJWT decodedJWT = mock(DecodedJWT.class);
        Claim typeClaim = mock(Claim.class);

        doReturn(decodedJWT).when(jwtUtils).validateToken("access-token-with-blank-sub");
        when(decodedJWT.getClaim("type")).thenReturn(typeClaim);
        when(typeClaim.asString()).thenReturn("access");
        when(decodedJWT.getSubject()).thenReturn("   ");

        assertThatThrownBy(() -> jwtUtils.validateAccessToken("access-token-with-blank-sub"))
                .isInstanceOf(InvalidTokenException.class)
                .hasMessage("Access token subject is missing or blank");
    }

    @Test
    void testValidateAccessToken_WithNonAccessType_ThrowsInvalidTokenException() {
        JwtUtils jwtUtils = spy(new JwtUtils());
        DecodedJWT decodedJWT = mock(DecodedJWT.class);
        Claim typeClaim = mock(Claim.class);

        doReturn(decodedJWT).when(jwtUtils).validateToken("refresh-token");
        when(decodedJWT.getClaim("type")).thenReturn(typeClaim);
        when(typeClaim.asString()).thenReturn("refresh");

        assertThatThrownBy(() -> jwtUtils.validateAccessToken("refresh-token"))
                .isInstanceOf(InvalidTokenException.class)
                .hasMessage("Token is not an access token");
    }
}
