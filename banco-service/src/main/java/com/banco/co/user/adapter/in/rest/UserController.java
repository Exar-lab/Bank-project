package com.banco.co.user.adapter.in.rest;

import com.banco.co.user.dto.customer.CustomerResponseDto;
import com.banco.co.user.dto.customer.CustomerUpdateDto;
import com.banco.co.user.dto.customer.PasswordRequestDto;
import com.banco.co.user.service.user.IUserService;
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
 *
 * NOTE: The legacy controller at com.banco.co.user.controller.UserController
 * is kept active during Phase 1 (additive). Both classes would conflict
 * at the same path — this class is provided for the target architecture
 * but must NOT be activated until the legacy controller is removed in a future phase.
 */
// @RestController — intentionally commented out during additive Phase 1.
// Uncomment and delete com.banco.co.user.controller.UserController in Phase 2.
@Validated
@RequestMapping("/api/v1/users/me")
public class UserController {

    private final IUserService userService;

    public UserController(IUserService userService) {
        this.userService = userService;
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('user:read', 'SCOPE_user:read')")
    public ResponseEntity<CustomerResponseDto> me(Authentication authentication) {
        return ResponseEntity.ok(userService.findUserByEmail(authentication.getName()));
    }

    @PutMapping
    @PreAuthorize("hasAnyAuthority('user:write', 'SCOPE_user:write')")
    public ResponseEntity<CustomerResponseDto> updateMe(
            @Valid @RequestBody CustomerUpdateDto dto,
            Authentication authentication) {
        return ResponseEntity.ok(userService.updateUser(authentication.getName(), dto));
    }

    @PutMapping("/password")
    @PreAuthorize("hasAnyAuthority('user:write', 'SCOPE_user:write')")
    public ResponseEntity<Void> updatePassword(
            @Valid @RequestBody PasswordRequestDto dto,
            Authentication authentication) {
        userService.updatePassword(dto, authentication.getName());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping
    @PreAuthorize("hasAnyAuthority('user:write', 'SCOPE_user:write')")
    public ResponseEntity<Void> deleteMe(Authentication authentication) {
        userService.deleteUserByEmail(authentication.getName());
        return ResponseEntity.noContent().build();
    }
}
