package com.banco.co.user.controller;

import com.banco.co.user.dto.customer.CustomerResponseDto;
import com.banco.co.user.dto.customer.CustomerUpdateDto;
import com.banco.co.user.dto.employee.EmployeeRequestDto;
import com.banco.co.user.dto.employee.EmployeeResponseDto;
import com.banco.co.user.enums.UserStatus;
import com.banco.co.user.service.user.IUserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Validated
@RestController
@RequestMapping("/api/v1/admin/users")
public class UserAdminController {

    private final IUserService userService;

    public UserAdminController(IUserService userService) {
        this.userService = userService;
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('user:read', 'SCOPE_user:read') and hasAnyRole('SYSTEM_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<CustomerResponseDto> getById(
            @PathVariable UUID id,
            Authentication authentication) {
        return ResponseEntity.ok(userService.getUserById(id, authentication.getName()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('user:write', 'SCOPE_user:write') and hasAnyRole('SYSTEM_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<CustomerResponseDto> updateByAdmin(
            @PathVariable UUID id,
            @Valid @RequestBody CustomerUpdateDto dto,
            Authentication authentication) {
        return ResponseEntity.ok(userService.updateUserByAdmin(id, dto, authentication.getName()));
    }

    @PutMapping("/{id}/suspend")
    @PreAuthorize("hasAnyAuthority('user:write', 'SCOPE_user:write') and hasAnyRole('SYSTEM_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<Void> suspend(
            @PathVariable UUID id,
            @RequestParam @NotBlank String reason,
            Authentication authentication) {
        userService.suspendUser(id, reason, authentication.getName());
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/activate")
    @PreAuthorize("hasAnyAuthority('user:write', 'SCOPE_user:write') and hasAnyRole('SYSTEM_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<Void> activate(
            @PathVariable UUID id,
            Authentication authentication) {
        userService.activateUser(id, authentication.getName());
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasAnyAuthority('user:write', 'SCOPE_user:write') and hasAnyRole('SYSTEM_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<CustomerResponseDto> updateStatus(
            @PathVariable UUID id,
            @RequestParam UserStatus status,
            Authentication authentication) {
        return ResponseEntity.ok(userService.updateUserStatus(id, status, authentication.getName()));
    }

    @PostMapping("/employees")
    @PreAuthorize("hasAnyAuthority('user:write', 'SCOPE_user:write') and hasAnyRole('SYSTEM_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<EmployeeResponseDto> createEmployee(
            @Valid @RequestBody EmployeeRequestDto dto,
            Authentication authentication) {
        EmployeeResponseDto response = userService.createUserByEmployee(authentication.getName(), dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
