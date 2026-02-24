package com.banco.co.user.service.user;

import com.banco.co.user.dto.customer.CustomerRequestDto;
import com.banco.co.user.dto.customer.CustomerResponseDto;
import com.banco.co.user.dto.customer.CustomerUpdateDto;
import com.banco.co.user.dto.customer.PasswordRequestDto;
import com.banco.co.user.dto.employee.EmployeeRequestDto;
import com.banco.co.user.dto.employee.EmployeeResponseDto;
import com.banco.co.user.enums.UserStatus;
import com.banco.co.user.model.User;

import java.util.UUID;

public interface IUserService {

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

    // ══════════════════════════════════════════════════════════
    // MÉTODOS AUXILIARES
    // ══════════════════════════════════════════════════════════

    User getEntityUserByEmail(String email);

    User getEntityUserByDocumentNumber(String documentNumber);
}
