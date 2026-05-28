package com.banco.co.user.domain.port.in;

import com.banco.co.user.dto.customer.CustomerRequestDto;
import com.banco.co.user.dto.customer.CustomerResponseDto;
import com.banco.co.user.dto.customer.CustomerUpdateDto;
import com.banco.co.user.dto.customer.PasswordRequestDto;
import com.banco.co.user.dto.employee.EmployeeRequestDto;
import com.banco.co.user.dto.employee.EmployeeResponseDto;
import com.banco.co.user.enums.UserStatus;

import java.util.UUID;

/**
 * Input port — defines all use cases exposed by the user feature.
 * No JPA imports, no Spring imports: only DTOs and domain types.
 */
public interface IUserUseCase {

    // ══════════════════════════════════════════════════════════
    // AUTO-REGISTRO PÚBLICO (Cliente)
    // ══════════════════════════════════════════════════════════

    CustomerResponseDto createUser(CustomerRequestDto dto);

    // ══════════════════════════════════════════════════════════
    // CREACIÓN DE EMPLEADO (Solo Admin)
    // ══════════════════════════════════════════════════════════

    EmployeeResponseDto createUserByEmployee(String creatorEmail, EmployeeRequestDto dto);

    // ══════════════════════════════════════════════════════════
    // OPERACIONES PROPIAS (Usuario autenticado)
    // ══════════════════════════════════════════════════════════

    CustomerResponseDto findUserByEmail(String email);

    CustomerResponseDto updateUser(String email, CustomerUpdateDto updates);

    void updatePassword(PasswordRequestDto dto, String email);

    void deleteUserByEmail(String email);

    // ══════════════════════════════════════════════════════════
    // OPERACIONES ADMINISTRATIVAS
    // ══════════════════════════════════════════════════════════

    CustomerResponseDto getUserById(UUID userId, String adminEmail);

    CustomerResponseDto updateUserByAdmin(UUID userId, CustomerUpdateDto dto, String adminEmail);

    void suspendUser(UUID userId, String reason, String adminEmail);

    void activateUser(UUID userId, String adminEmail);

    CustomerResponseDto updateUserStatus(UUID userId, UserStatus status, String adminEmail);

}
