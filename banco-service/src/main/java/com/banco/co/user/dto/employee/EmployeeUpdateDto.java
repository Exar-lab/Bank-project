package com.banco.co.user.dto.employee;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record EmployeeUpdateDto(

        @Size(max = 50, message = "First name must not exceed 50 characters") @Pattern(regexp = "^[a-zA-ZáéíóúÁÉÍÓÚñÑüÜ\\s]+$", message = "First name must only contain letters") String fistName,

        @Size(max = 50, message = "Last name must not exceed 50 characters") @Pattern(regexp = "^[a-zA-ZáéíóúÁÉÍÓÚñÑüÜ\\s]+$", message = "Last name must only contain letters") String lastName,

        @Size(max = 20, message = "Phone number must not exceed 20 characters") @Pattern(regexp = "^\\+?[0-9]{7,15}$", message = "Phone number must contain only numbers, optionally starting with +") String phoneNumber,

        @Size(max = 200, message = "Address must not exceed 200 characters") @Pattern(regexp = "^[a-zA-Z0-9áéíóúÁÉÍÓÚñÑüÜ\\s,\\.#\\-]+$", message = "Address contains invalid characters") String address) {
}
