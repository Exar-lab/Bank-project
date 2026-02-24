package com.banco.co.user.dto.customer;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PasswordRequestDto(

        @NotBlank(message = "Current password is required") String currentPassword,

        @NotBlank(message = "New password is required") @Size(min = 8, max = 100, message = "Password must be between 8 and 100 characters") @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&#+\\-_.])[A-Za-z\\d@$!%*?&#+\\-_.]{8,}$", message = "Password must contain at least one uppercase letter, one lowercase letter, one number, and one special character (@$!%*?&#+\\-_.)") String password,

        @NotBlank(message = "Password confirmation is required") @Size(min = 8, max = 100, message = "Password confirmation must be between 8 and 100 characters") @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&#+\\-_.])[A-Za-z\\d@$!%*?&#+\\-_.]{8,}$", message = "Password must contain at least one uppercase letter, one lowercase letter, one number, and one special character (@$!%*?&#+\\-_.)") String confirmPassword) {
}
