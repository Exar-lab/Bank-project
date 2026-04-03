package com.banco.co.utils;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.*;
import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.banco.co.exception.authentication.InvalidPrincipalException;
import com.banco.co.exception.authentication.InvalidTokenException;
import com.banco.co.user.model.UserCredential;
import com.banco.co.user.model.adapter.SecurityUser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Component
@Slf4j
public class JwtUtils {

    @Value("${security.jwt.secret-key}")
    private String secretKey;

    @Value("${security.jwt.issuer}")
    private String issuer;

    @Value("${security.jwt.access-token.expiration-minutes}")
    private int accessTokenExpirationMinutes;

    @Value("${security.jwt.refresh-token.expiration-days}")
    private int refreshTokenExpirationDays;

    // ═══════════════════════════════════════════════════════════
    //  ACCESS TOKEN (corto, con claims completos)
    // ═══════════════════════════════════════════════════════════

    public String generateAccessToken(Authentication authentication, Map<String, Object> additionalClaims) {
        Algorithm algorithm = Algorithm.HMAC256(secretKey);

        SecurityUser securityUser = (SecurityUser) authentication.getPrincipal();
        if (securityUser == null) {
            throw new InvalidPrincipalException("Principal not found");
        }
        UserCredential credential = securityUser.getUser();


        Instant now = Instant.now();
        Instant expiration = now.plus(accessTokenExpirationMinutes, ChronoUnit.MINUTES);

        // Separar roles de scopes
        List<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority).filter(Objects::nonNull)
                .filter(auth -> auth.startsWith("ROLE_"))
                .collect(Collectors.toList());

        List<String> scopes = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority).filter(Objects::nonNull)
                .filter(auth -> !auth.startsWith("ROLE_"))
                .collect(Collectors.toList());

        var builder = JWT.create()
                .withIssuer(issuer)
                .withSubject(credential.getEmail())
                .withClaim("userId", credential.getId().toString())
                .withClaim("email", credential.getUser().getEmail())
                .withClaim("username", credential.getUser().getUsername())
                .withClaim("roles", roles)
                .withClaim("scope", scopes)
                .withClaim("type", "access")
                .withIssuedAt(Date.from(now))
                .withExpiresAt(Date.from(expiration))
                .withNotBefore(Date.from(now))
                .withJWTId(UUID.randomUUID().toString());


        String token = builder.sign(algorithm);

        log.info("Access token generated for user: {} (expires in {} minutes)",
                credential.getEmail(), accessTokenExpirationMinutes);

        return token;
    }

    // Sobrecarga sin claims adicionales
    public String generateAccessToken(Authentication authentication) {
        return generateAccessToken(authentication, null);
    }

    // ═══════════════════════════════════════════════════════════
    //  REFRESH TOKEN (largo, solo con info mínima)
    // ═══════════════════════════════════════════════════════════

    public String generateRefreshToken(String username, UUID userId) {
        Algorithm algorithm = Algorithm.HMAC256(secretKey);

        Instant now = Instant.now();
        Instant expiration = now.plus(refreshTokenExpirationDays, ChronoUnit.DAYS);

        String token = JWT.create()
                .withIssuer(issuer)
                .withSubject(username)
                .withClaim("userId", userId.toString())
                .withClaim("type", "refresh")
                .withIssuedAt(Date.from(now))
                .withExpiresAt(Date.from(expiration))
                .withJWTId(UUID.randomUUID().toString())
                .sign(algorithm);

        log.info("Refresh token generated for user: {} (expires in {} days)",
                username, refreshTokenExpirationDays);

        return token;
    }

    // ═══════════════════════════════════════════════════════════
    //  VALIDACIÓN
    // ═══════════════════════════════════════════════════════════

    public DecodedJWT validateToken(String token) {
        try {
            Algorithm algorithm = Algorithm.HMAC256(secretKey);

            JWTVerifier verifier = JWT.require(algorithm)
                    .withIssuer(issuer)
                    .build();

            DecodedJWT decodedJWT = verifier.verify(token);

            log.debug("Token validated successfully for user: {}", decodedJWT.getSubject());

            return decodedJWT;

        } catch (TokenExpiredException e) {
            log.warn("Token expired: {}", e.getMessage());
            throw new TokenExpiredException("Token has expired",Instant.now());

        } catch (SignatureVerificationException e) {
            log.error("Invalid token signature: {}", e.getMessage());
            throw new InvalidTokenException("Invalid token signature", e);

        } catch (AlgorithmMismatchException e) {
            log.error("Token algorithm mismatch: {}", e.getMessage());
            throw new InvalidTokenException("Token algorithm mismatch", e);

        } catch (JWTVerificationException e) {
            log.error("Token verification failed: {}", e.getMessage());
            throw new InvalidTokenException("Invalid token", e);
        }
    }

    // Validar específicamente que sea access token
    public DecodedJWT validateAccessToken(String token) {
        DecodedJWT decodedJWT = validateToken(token);

        String tokenType = decodedJWT.getClaim("type").asString();
        if (!"access".equals(tokenType)) {
            throw new InvalidTokenException("Token is not an access token");
        }

        String subject = decodedJWT.getSubject();
        if (subject == null || subject.isBlank()) {
            throw new InvalidTokenException("Access token subject is missing or blank");
        }

        return decodedJWT;
    }

    // Validar específicamente que sea refresh token
    public DecodedJWT validateRefreshToken(String token) {
        DecodedJWT decodedJWT = validateToken(token);

        String tokenType = decodedJWT.getClaim("type").asString();
        if (!"refresh".equals(tokenType)) {
            throw new InvalidTokenException("Token is not a refresh token");
        }

        return decodedJWT;
    }

    // ═══════════════════════════════════════════════════════════
    //  EXTRACCIÓN DE CLAIMS
    // ═══════════════════════════════════════════════════════════

    public String getUsername(DecodedJWT token) {
        return token.getSubject();
    }

    public UUID getUserId(DecodedJWT token) {
        String userId = token.getClaim("userId").asString();
        return userId != null ? UUID.fromString(userId) : null;
    }

    public String getEmail(DecodedJWT token) {
        return token.getClaim("email").asString();
    }

    public List<String> getRoles(DecodedJWT token) {
        Claim rolesClaim = token.getClaim("roles");
        return rolesClaim.isNull() ? List.of() : rolesClaim.asList(String.class);
    }

    public List<String> getScopes(DecodedJWT token) {
        Claim scopeClaim = token.getClaim("scope");
        if (!scopeClaim.isMissing() && !scopeClaim.isNull()) {
            List<String> scopeList = scopeClaim.asList(String.class);
            if (scopeList != null) {
                return scopeList;
            }

            String scopeText = scopeClaim.asString();
            if (scopeText != null && !scopeText.isBlank()) {
                return Arrays.stream(scopeText.trim().split("\\s+"))
                        .filter(value -> !value.isBlank())
                        .toList();
            }
        }

        Claim scopesClaim = token.getClaim("scopes");
        return scopesClaim.isMissing() || scopesClaim.isNull() ? List.of() : scopesClaim.asList(String.class);
    }

    public String getTokenId(DecodedJWT token) {
        return token.getId();
    }

    public Date getExpirationDate(DecodedJWT token) {
        return token.getExpiresAt();
    }

    public Date getIssuedAt(DecodedJWT token) {
        return token.getIssuedAt();
    }

    public Claim getClaim(DecodedJWT token, String claimName) {
        return token.getClaim(claimName);
    }

    public Map<String, Claim> getAllClaims(DecodedJWT token) {
        return token.getClaims();
    }

    // ═══════════════════════════════════════════════════════════
    //  UTILIDADES
    // ═══════════════════════════════════════════════════════════

    public boolean isTokenExpired(DecodedJWT token) {
        return token.getExpiresAt().before(new Date());
    }

    public long getTimeUntilExpiration(DecodedJWT token) {
        Date expiration = token.getExpiresAt();
        Date now = new Date();
        return expiration.getTime() - now.getTime(); // milisegundos
    }

    public boolean isRefreshToken(DecodedJWT token) {
        return "refresh".equals(token.getClaim("type").asString());
    }

    public boolean isAccessToken(DecodedJWT token) {
        return "access".equals(token.getClaim("type").asString());
    }
}
