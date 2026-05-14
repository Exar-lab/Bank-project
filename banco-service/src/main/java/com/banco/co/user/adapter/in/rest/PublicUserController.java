package com.banco.co.user.adapter.in.rest;

import com.banco.co.user.dto.customer.CustomerRequestDto;
import com.banco.co.user.dto.customer.CustomerResponseDto;
import com.banco.co.user.service.user.IUserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST adapter for public (unauthenticated) user endpoints.
 * Migrated from com.banco.co.user.controller.PublicUserController.
 * @RequestMapping paths are identical — no API contract change.
 *
 * NOTE: See UserController note about Phase 1 / Phase 2 activation.
 * The @RestController annotation is intentionally commented out during Phase 1.
 */
// @RestController — intentionally commented out during additive Phase 1.
@Validated
@RequestMapping("/api/v1/public/users")
public class PublicUserController {

    private final IUserService userService;

    public PublicUserController(IUserService userService) {
        this.userService = userService;
    }

    @PostMapping("/register")
    @PreAuthorize("permitAll()")
    public ResponseEntity<CustomerResponseDto> register(
            @Valid @RequestBody CustomerRequestDto dto) {
        CustomerResponseDto response = userService.createUser(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
