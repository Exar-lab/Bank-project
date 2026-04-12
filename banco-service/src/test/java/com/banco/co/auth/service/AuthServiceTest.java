package com.banco.co.auth.service;

import com.auth0.jwt.interfaces.DecodedJWT;
import com.banco.co.auth.dto.LoginRequestDto;
import com.banco.co.auth.dto.RefreshRequestDto;
import com.banco.co.auth.dto.TokenPairResponseDto;
import com.banco.co.exception.authentication.AccountLockedException;
import com.banco.co.exception.authentication.InvalidTokenException;
import com.banco.co.exception.authentication.TokenExpiredException;
import com.banco.co.security.token.enums.RefreshTokenRevocationReason;
import com.banco.co.security.token.model.RefreshToken;
import com.banco.co.security.token.repository.IRefreshTokenRepository;
import com.banco.co.user.model.User;
import com.banco.co.user.model.UserCredential;
import com.banco.co.user.model.adapter.SecurityUser;
import com.banco.co.utils.JwtUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

class AuthServiceTest {

    private AuthenticationManager authenticationManager;
    private JwtUtils jwtUtils;
    private IRefreshTokenRepository refreshTokenRepository;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        authenticationManager = mock(AuthenticationManager.class);
        jwtUtils = mock(JwtUtils.class);
        refreshTokenRepository = mock(IRefreshTokenRepository.class);
        authService = new AuthService(authenticationManager, jwtUtils, refreshTokenRepository);
    }

    @Test
    void testRefresh_WhenTokenActive_RotatesAndReturnsNewPair() {
        UserCredential userCredential = buildUserCredential("user@banco.co");

        RefreshToken storedToken = new RefreshToken();
        storedToken.setId(UUID.randomUUID());
        storedToken.setUserCredential(userCredential);
        storedToken.setJti("old-jti");
        storedToken.setTokenHash(sha256("old-refresh"));
        storedToken.setExpiresAt(LocalDateTime.now().plusMinutes(10));
        storedToken.setRevoked(false);

        DecodedJWT oldDecoded = mock(DecodedJWT.class);
        when(oldDecoded.getId()).thenReturn("old-jti");

        DecodedJWT newDecoded = mock(DecodedJWT.class);
        when(newDecoded.getId()).thenReturn("new-jti");
        when(newDecoded.getExpiresAt()).thenReturn(Date.from(LocalDateTime.now().plusDays(1).toInstant(ZoneOffset.UTC)));

        when(jwtUtils.validateRefreshToken("old-refresh")).thenReturn(oldDecoded);
        when(refreshTokenRepository.findByJtiForUpdate("old-jti")).thenReturn(Optional.of(storedToken));
        when(jwtUtils.generateAccessToken(any(Authentication.class))).thenReturn("new-access");
        when(jwtUtils.generateRefreshToken("user@banco.co", userCredential.getId())).thenReturn("new-refresh");
        when(jwtUtils.validateRefreshToken("new-refresh")).thenReturn(newDecoded);
        when(jwtUtils.getTokenId(oldDecoded)).thenReturn("old-jti");
        when(jwtUtils.getTokenId(newDecoded)).thenReturn("new-jti");
        when(jwtUtils.getAccessTokenExpirationSeconds()).thenReturn(900L);

        TokenPairResponseDto result = authService.refresh(new RefreshRequestDto("old-refresh"));

        assertThat(result.accessToken()).isEqualTo("new-access");
        assertThat(result.refreshToken()).isEqualTo("new-refresh");
        assertThat(result.tokenType()).isEqualTo("Bearer");

        verify(refreshTokenRepository).revokeById(
                eq(storedToken.getId()),
                eq(RefreshTokenRevocationReason.ROTATED),
                any(LocalDateTime.class),
                eq("new-jti")
        );

        ArgumentCaptor<RefreshToken> tokenCaptor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(tokenCaptor.capture());
        RefreshToken saved = tokenCaptor.getValue();
        assertThat(saved.getUserCredential().getId()).isEqualTo(userCredential.getId());
        assertThat(saved.getJti()).isEqualTo("new-jti");
        assertThat(saved.getTokenHash()).isEqualTo(sha256("new-refresh"));
        assertThat(saved.getParentJti()).isEqualTo("old-jti");
    }

    @Test
    void testRefresh_WhenRevokedTokenReuseDetected_RevokesAllActiveAndThrowsUnauthorized() {
        UserCredential userCredential = buildUserCredential("user@banco.co");

        RefreshToken storedToken = new RefreshToken();
        storedToken.setId(UUID.randomUUID());
        storedToken.setUserCredential(userCredential);
        storedToken.setJti("reused-jti");
        storedToken.setTokenHash(sha256("reused-refresh"));
        storedToken.setExpiresAt(LocalDateTime.now().plusMinutes(10));
        storedToken.setRevoked(true);

        DecodedJWT decoded = mock(DecodedJWT.class);
        when(decoded.getId()).thenReturn("reused-jti");

        when(jwtUtils.validateRefreshToken("reused-refresh")).thenReturn(decoded);
        when(jwtUtils.getTokenId(decoded)).thenReturn("reused-jti");
        when(refreshTokenRepository.findByJtiForUpdate("reused-jti")).thenReturn(Optional.of(storedToken));

        assertThatThrownBy(() -> authService.refresh(new RefreshRequestDto("reused-refresh")))
                .isInstanceOf(InvalidTokenException.class)
                .hasMessage("Refresh token reuse detected");

        verify(refreshTokenRepository).revokeAllActiveByUser(
                org.mockito.ArgumentMatchers.eq(userCredential.getId()),
                org.mockito.ArgumentMatchers.eq(RefreshTokenRevocationReason.REUSE_DETECTED),
                any(LocalDateTime.class)
        );
    }

    @Test
    void testRefresh_WhenStoredTokenIsExpired_RevokesAsExpiredAndThrowsUnauthorized() {
        UserCredential userCredential = buildUserCredential("user@banco.co");

        RefreshToken storedToken = new RefreshToken();
        storedToken.setId(UUID.randomUUID());
        storedToken.setUserCredential(userCredential);
        storedToken.setJti("expired-jti");
        storedToken.setTokenHash(sha256("expired-refresh"));
        storedToken.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        storedToken.setRevoked(false);

        DecodedJWT decoded = mock(DecodedJWT.class);
        when(decoded.getId()).thenReturn("expired-jti");

        when(jwtUtils.validateRefreshToken("expired-refresh")).thenReturn(decoded);
        when(jwtUtils.getTokenId(decoded)).thenReturn("expired-jti");
        when(refreshTokenRepository.findByJtiForUpdate("expired-jti")).thenReturn(Optional.of(storedToken));

        assertThatThrownBy(() -> authService.refresh(new RefreshRequestDto("expired-refresh")))
                .isInstanceOf(TokenExpiredException.class)
                .hasMessage("Refresh token has expired");

        verify(refreshTokenRepository).revokeById(
                eq(storedToken.getId()),
                eq(RefreshTokenRevocationReason.EXPIRED),
                any(LocalDateTime.class),
                eq(null)
        );
        verify(jwtUtils, never()).generateAccessToken(any(Authentication.class));
        verify(jwtUtils, never()).generateRefreshToken(anyString(), any(UUID.class));
        verify(refreshTokenRepository, never()).save(any(RefreshToken.class));
    }

    @Test
    void testRefresh_WhenTokenSignatureIsInvalid_ThrowsUnauthorizedAndDoesNotRotate() {
        when(jwtUtils.validateRefreshToken("invalid-refresh"))
                .thenThrow(new InvalidTokenException("Invalid refresh token"));

        assertThatThrownBy(() -> authService.refresh(new RefreshRequestDto("invalid-refresh")))
                .isInstanceOf(InvalidTokenException.class)
                .hasMessage("Invalid refresh token");

        verify(refreshTokenRepository, never()).findByJtiForUpdate(anyString());
        verify(jwtUtils, never()).generateAccessToken(any(Authentication.class));
        verify(jwtUtils, never()).generateRefreshToken(anyString(), any(UUID.class));
        verify(refreshTokenRepository, never()).save(any(RefreshToken.class));
    }

    @Test
    void testLogout_WhenTokenIsValid_RevokesTokenAndReturnsSilently() {
        UserCredential userCredential = buildUserCredential("user@banco.co");

        RefreshToken storedToken = new RefreshToken();
        storedToken.setId(UUID.randomUUID());
        storedToken.setUserCredential(userCredential);
        storedToken.setJti("logout-jti");
        storedToken.setTokenHash(sha256("logout-token"));
        storedToken.setExpiresAt(LocalDateTime.now().plusMinutes(10));
        storedToken.setRevoked(false);

        DecodedJWT decoded = mock(DecodedJWT.class);
        when(decoded.getId()).thenReturn("logout-jti");
        when(jwtUtils.validateRefreshToken("logout-token")).thenReturn(decoded);
        when(jwtUtils.getTokenId(decoded)).thenReturn("logout-jti");
        when(refreshTokenRepository.findByJtiForUpdate("logout-jti")).thenReturn(Optional.of(storedToken));

        authService.logout(new RefreshRequestDto("logout-token"));

        verify(refreshTokenRepository).revokeById(
                org.mockito.ArgumentMatchers.eq(storedToken.getId()),
                org.mockito.ArgumentMatchers.eq(RefreshTokenRevocationReason.LOGOUT),
                any(LocalDateTime.class),
                org.mockito.ArgumentMatchers.isNull()
        );
    }

    @Test
    void testLogout_WhenTokenIsInvalid_IgnoresAndDoesNotFail() {
        when(jwtUtils.validateRefreshToken(anyString())).thenThrow(new InvalidTokenException("invalid"));

        authService.logout(new RefreshRequestDto("invalid-token"));

        verify(refreshTokenRepository, never()).findByJtiForUpdate(anyString());
    }

    @Test
    void testLogin_WhenAccountIsLocked_ThrowsAccountLockedException() {
        when(authenticationManager.authenticate(any(Authentication.class)))
                .thenThrow(new LockedException("locked"));

        assertThatThrownBy(() -> authService.login(new LoginRequestDto("locked@banco.co", "secret")))
                .isInstanceOf(AccountLockedException.class);
    }

    private UserCredential buildUserCredential(String email) {
        User user = new User();
        user.setEmail(email);

        UserCredential credential = new UserCredential();
        credential.setId(UUID.randomUUID());
        credential.setEmail(email);
        credential.setUser(user);
        credential.setRoles(new HashSet<>());
        credential.setAccountNonLocked(true);
        credential.setEnabled(true);
        credential.setAccountNonExpired(true);
        credential.setCredentialsNonExpired(true);
        return credential;
    }

    private String sha256(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
