package com.banco.co.user.adapter.in.rest;

import com.banco.co.user.domain.port.in.IUserUseCase;
import com.banco.co.user.dto.customer.CustomerRequestDto;
import com.banco.co.user.dto.customer.CustomerResponseDto;
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
 * Hexagonal adapter — REST input port for public (unauthenticated) user endpoints.
 * Legacy com.banco.co.user.controller.PublicUserController has been removed — this is now the active controller.
 */
@RestController
@Validated
@RequestMapping("/api/v1/public/users")
public class PublicUserController {

    private final IUserUseCase userUseCase;

    public PublicUserController(IUserUseCase userUseCase) {
        this.userUseCase = userUseCase;
    }

    @PostMapping("/register")
    @PreAuthorize("permitAll()")
    public ResponseEntity<CustomerResponseDto> register(
            @Valid @RequestBody CustomerRequestDto dto) {
        CustomerResponseDto response = userUseCase.createUser(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
