package com.banco.co.transaction.controller;

import com.banco.co.transaction.dto.TransactionRequestMetadataDto;
import jakarta.servlet.http.HttpServletRequest;

public final class TransactionMetadataExtractor {

    private TransactionMetadataExtractor() {}

    public static TransactionRequestMetadataDto extract(HttpServletRequest request) {
        String clientIp = request.getHeader("X-Forwarded-For");
        if (clientIp != null && !clientIp.isBlank()) {
            int commaIndex = clientIp.indexOf(',');
            clientIp = commaIndex > -1
                    ? clientIp.substring(0, commaIndex).trim()
                    : clientIp.trim();
            if (clientIp.isEmpty()) {
                clientIp = request.getRemoteAddr();
            }
        } else {
            clientIp = request.getRemoteAddr();
        }
        return new TransactionRequestMetadataDto(
                clientIp,
                request.getHeader("User-Agent"),
                request.getHeader("X-Device-Id")
        );
    }
}
