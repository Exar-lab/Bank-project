package com.banco.co.auth.dto;

public record TokenPairResponseDto(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn
) {
}
