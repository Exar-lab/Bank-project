package com.banco.co.user.adapter.in.rest;

import com.banco.co.user.domain.port.in.IUserUseCase;
import com.banco.co.user.dto.customer.CustomerResponseDto;
import com.banco.co.user.dto.customer.CustomerUpdateDto;
import com.banco.co.user.dto.customer.PasswordRequestDto;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST adapter for authenticated user operations.
 * Migrated from com.banco.co.user.controller.UserController.
 * @RequestMapping paths are identical — no API contract change.
 */
@RestController
@Validated
@RequestMapping("/api/v1/users/me")
public class UserController {

    private final IUserUseCase userUseCase;

    public UserController(IUserUseCase userUseCase) {
        this.userUseCase = userUseCase;
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('user:read', 'SCOPE_user:read')")
    public ResponseEntity<CustomerResponseDto> me(Authentication authentication) {
        return ResponseEntity.ok(userUseCase.findUserByEmail(authentication.getName()));
    }

    @PutMapping
    @PreAuthorize("hasAnyAuthority('user:write', 'SCOPE_user:write')")
    public ResponseEntity<CustomerResponseDto> updateMe(
            @Valid @RequestBody CustomerUpdateDto dto,
            Authentication authentication) {
        return ResponseEntity.ok(userUseCase.updateUser(authentication.getName(), dto));
    }

    @PutMapping("/password")
    @PreAuthorize("hasAnyAuthority('user:write', 'SCOPE_user:write')")
    public ResponseEntity<Void> updatePassword(
            @Valid @RequestBody PasswordRequestDto dto,
            Authentication authentication) {
        userUseCase.updatePassword(dto, authentication.getName());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping
    @PreAuthorize("hasAnyAuthority('user:write', 'SCOPE_user:write')")
    public ResponseEntity<Void> deleteMe(Authentication authentication) {
        userUseCase.deleteUserByEmail(authentication.getName());
        return ResponseEntity.noContent().build();
    }
}
