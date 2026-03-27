package com.banco.co.transaction.dto;

/**
 * Transport-agnostic metadata extracted from the HTTP request in the Presentation layer.
 * The Application layer receives this record instead of HttpServletRequest,
 * keeping the use-case core independent of the web transport mechanism.
 */
public record TransactionRequestMetadataDto(
        String ipAddress,
        String userAgent,
        String deviceId
) {}
