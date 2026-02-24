package com.banco.co.user.dto.employee;

import com.banco.co.role.enums.SystemRole;
import com.banco.co.user.enums.DocumentType;
import jakarta.validation.constraints.*;

import java.time.LocalDate;

public record EmployeeRequestDto(

        @NotBlank(message = "First name is required") @Size(max = 50, message = "First name must not exceed 50 characters") @Pattern(regexp = "^[a-zA-ZáéíóúÁÉÍÓÚñÑüÜ\\s]+$", message = "First name must only contain letters") String fistName,

        @NotBlank(message = "Last name is required") @Size(max = 50, message = "Last name must not exceed 50 characters") @Pattern(regexp = "^[a-zA-ZáéíóúÁÉÍÓÚñÑüÜ\\s]+$", message = "Last name must only contain letters") String lastName,

        @NotBlank(message = "Email is required") @Email(message = "Email must be valid") @Size(max = 100, message = "Email must not exceed 100 characters") String email,

        @NotBlank(message = "Password is required") @Size(min = 8, max = 100, message = "Password must be between 8 and 100 characters") @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&#+\\-_.])[A-Za-z\\d@$!%*?&#+\\-_.]{8,}$", message = "Password must contain at least one uppercase letter, one lowercase letter, one number, and one special character (@$!%*?&#+\\-_.)") String password,

        @NotBlank(message = "Document number is required") @Size(max = 12, message = "Document number must not exceed 12 characters") @Pattern(regexp = "^[0-9]+$", message = "Document number must contain only numbers") String documentNumber,

        @NotNull(message = "Document type is required") DocumentType documentType,

        @NotNull(message = "Birth date is required") @Past(message = "Birth date must be in the past") LocalDate birthDate,

        @NotBlank(message = "Phone number is required") @Size(max = 20, message = "Phone number must not exceed 20 characters") @Pattern(regexp = "^\\+?[0-9]{7,15}$", message = "Phone number must contain only numbers, optionally starting with +") String phoneNumber,

        @NotBlank(message = "Address is required") @Size(max = 200, message = "Address must not exceed 200 characters") @Pattern(regexp = "^[a-zA-Z0-9áéíóúÁÉÍÓÚñÑüÜ\\s,\\.#\\-]+$", message = "Address contains invalid characters") String address,

        @NotNull(message = "Role is required") SystemRole role) {
}
