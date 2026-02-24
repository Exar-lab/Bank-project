package com.banco.co.user.dto.customer;

import com.banco.co.user.enums.DocumentType;
import com.banco.co.user.enums.KycStatus;
import com.banco.co.user.enums.UserStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record CustomerResponseDto(
        UUID id,
        String userCode,
        String fistName,
        String lastName,
        String username,
        String email,
        String documentNumber,
        DocumentType documentType,
        LocalDate birthDate,
        String phoneNumber,
        String address,
        UserStatus status,
        KycStatus kycStatus,
        LocalDateTime createdDate,
        LocalDateTime updatedDate) {
}
