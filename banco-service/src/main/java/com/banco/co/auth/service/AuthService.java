package com.banco.co.auth.service;

import com.auth0.jwt.interfaces.DecodedJWT;
import com.banco.co.auth.dto.LoginRequestDto;
import com.banco.co.auth.dto.RefreshRequestDto;
import com.banco.co.auth.dto.TokenPairResponseDto;
import com.banco.co.exception.authentication.AccountLockedException;
import com.banco.co.exception.authentication.InvalidCredentialsException;
import com.banco.co.exception.authentication.InvalidTokenException;
import com.banco.co.exception.authentication.TokenExpiredException;
import com.banco.co.security.token.enums.RefreshTokenRevocationReason;
import com.banco.co.security.token.model.RefreshToken;
import com.banco.co.security.token.repository.IRefreshTokenRepository;
import com.banco.co.user.model.UserCredential;
import com.banco.co.user.model.adapter.SecurityUser;
import com.banco.co.utils.JwtUtils;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtUtils jwtUtils;
    private final IRefreshTokenRepository refreshTokenRepository;

    public AuthService(AuthenticationManager authenticationManager,
                       JwtUtils jwtUtils,
                       IRefreshTokenRepository refreshTokenRepository) {
        this.authenticationManager = authenticationManager;
        this.jwtUtils = jwtUtils;
        this.refreshTokenRepository = refreshTokenRepository;
    }

    @Transactional
    public TokenPairResponseDto login(LoginRequestDto request) {
        Authentication authentication = authenticate(request.email(), request.password());
        SecurityUser securityUser = (SecurityUser) authentication.getPrincipal();
        UserCredential userCredential = securityUser.getUser();

        String accessToken = jwtUtils.generateAccessToken(authentication);
        String refreshToken = jwtUtils.generateRefreshToken(userCredential.getEmail(), userCredential.getId());

        persistRefreshToken(
                userCredential,
                jwtUtils.validateRefreshToken(refreshToken),
                refreshToken,
                null
        );

        return new TokenPairResponseDto(
                accessToken,
                refreshToken,
                "Bearer",
                jwtUtils.getAccessTokenExpirationSeconds()
        );
    }

    @Transactional
    public TokenPairResponseDto refresh(RefreshRequestDto request) {
        DecodedJWT decodedRefreshToken = jwtUtils.validateRefreshToken(request.refreshToken());
        String jti = requiredJti(decodedRefreshToken);
        String tokenHash = sha256(request.refreshToken());

        RefreshToken storedToken = refreshTokenRepository.findByJtiForUpdate(jti)
                .orElseThrow(() -> new InvalidTokenException("Refresh token not recognized"));

        if (!tokenHash.equals(storedToken.getTokenHash())) {
            throw new InvalidTokenException("Refresh token hash mismatch");
        }

        if (storedToken.isRevoked()) {
            refreshTokenRepository.revokeAllActiveByUser(
                    storedToken.getUserCredential().getId(),
                    RefreshTokenRevocationReason.REUSE_DETECTED,
                    LocalDateTime.now()
            );
            throw new InvalidTokenException("Refresh token reuse detected");
        }

        if (storedToken.isExpired()) {
            refreshTokenRepository.revokeById(
                    storedToken.getId(),
                    RefreshTokenRevocationReason.EXPIRED,
                    LocalDateTime.now(),
                    null
            );
            throw new TokenExpiredException("Refresh token has expired");
        }

        UserCredential userCredential = storedToken.getUserCredential();
        Authentication authentication = buildAuthenticatedPrincipal(userCredential);

        String newAccessToken = jwtUtils.generateAccessToken(authentication);
        String newRefreshToken = jwtUtils.generateRefreshToken(userCredential.getEmail(), userCredential.getId());
        DecodedJWT newDecodedRefreshToken = jwtUtils.validateRefreshToken(newRefreshToken);
        String newJti = requiredJti(newDecodedRefreshToken);

        refreshTokenRepository.revokeById(
                storedToken.getId(),
                RefreshTokenRevocationReason.ROTATED,
                LocalDateTime.now(),
                newJti
        );

        persistRefreshToken(userCredential, newDecodedRefreshToken, newRefreshToken, storedToken.getJti());

        return new TokenPairResponseDto(
                newAccessToken,
                newRefreshToken,
                "Bearer",
                jwtUtils.getAccessTokenExpirationSeconds()
        );
    }

    @Transactional
    public void logout(RefreshRequestDto request) {
        try {
            DecodedJWT decodedRefreshToken = jwtUtils.validateRefreshToken(request.refreshToken());
            String jti = requiredJti(decodedRefreshToken);
            String tokenHash = sha256(request.refreshToken());

            refreshTokenRepository.findByJtiForUpdate(jti)
                    .filter(token -> !token.isRevoked())
                    .filter(token -> tokenHash.equals(token.getTokenHash()))
                    .ifPresent(token -> refreshTokenRepository.revokeById(
                            token.getId(),
                            RefreshTokenRevocationReason.LOGOUT,
                            LocalDateTime.now(),
                            null
                    ));
        } catch (InvalidTokenException | TokenExpiredException ignored) {
            // 204 idempotente para minimizar fricción en logout.
        }
    }

    private Authentication authenticate(String email, String password) {
        try {
            return authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, password)
            );
        } catch (LockedException ex) {
            throw new AccountLockedException(email);
        } catch (BadCredentialsException ex) {
            throw new InvalidCredentialsException(email);
        } catch (AuthenticationException ex) {
            throw new InvalidCredentialsException(email);
        }
    }

    private Authentication buildAuthenticatedPrincipal(UserCredential userCredential) {
        SecurityUser securityUser = new SecurityUser(userCredential);
        return new UsernamePasswordAuthenticationToken(
                securityUser,
                null,
                securityUser.getAuthorities()
        );
    }

    private void persistRefreshToken(UserCredential userCredential,
                                     DecodedJWT decodedRefreshToken,
                                     String rawRefreshToken,
                                     String parentJti) {
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUserCredential(userCredential);
        refreshToken.setJti(requiredJti(decodedRefreshToken));
        refreshToken.setTokenHash(sha256(rawRefreshToken));
        refreshToken.setExpiresAt(toLocalDateTimeUtc(decodedRefreshToken.getExpiresAt()));
        refreshToken.setParentJti(parentJti);
        refreshTokenRepository.save(refreshToken);
    }

    private String requiredJti(DecodedJWT token) {
        String jti = jwtUtils.getTokenId(token);
        if (jti == null || jti.isBlank()) {
            throw new InvalidTokenException("Refresh token jti is missing");
        }
        return jti;
    }

    private LocalDateTime toLocalDateTimeUtc(Date date) {
        if (date == null) {
            throw new InvalidTokenException("Refresh token expiration is missing");
        }
        return LocalDateTime.ofInstant(date.toInstant(), ZoneOffset.UTC);
    }

    private String sha256(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm unavailable", e);
        }
    }
}
