package com.banco.co.account.adapter.in.rest;

import com.banco.co.account.dto.AccountResponseDto;
import com.banco.co.account.enums.AccountStatus;
import com.banco.co.account.domain.port.in.IAccountUseCase;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.UUID;

/**
 * Hexagonal adapter — REST input port for Account admin use cases.
 *
 * NOTE: @RestController is intentionally commented out to avoid duplicate URL
 * mapping with com.banco.co.account.controller.AccountAdminController during the
 * additive migration phase. Uncomment once the legacy controller is removed.
 *
 * Depends on IAccountUseCase (domain input port) instead of legacy IAccountService.
 */
@Validated
// @RestController   ← uncomment after removing legacy com.banco.co.account.controller.AccountAdminController
// @RequestMapping("/api/v1/admin/accounts")
public class AccountAdminController {

    private final IAccountUseCase accountUseCase;

    public AccountAdminController(IAccountUseCase accountUseCase) {
        this.accountUseCase = accountUseCase;
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasAnyAuthority('account:write', 'SCOPE_account:write') and hasAnyRole('SYSTEM_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<AccountResponseDto> updateStatus(
            @PathVariable UUID id,
            @RequestParam AccountStatus status,
            Authentication authentication) {
        return ResponseEntity.ok(accountUseCase.updateAccountStatus(id, status, authentication.getName()));
    }

    @PostMapping("/{id}/close")
    @PreAuthorize("hasAnyAuthority('account:write', 'SCOPE_account:write') and hasAnyRole('SYSTEM_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<Void> closeByAdmin(
            @PathVariable UUID id,
            Authentication authentication) {
        accountUseCase.closeAccountByAdmin(id, authentication.getName());
        return ResponseEntity.noContent().build();
    }
}
