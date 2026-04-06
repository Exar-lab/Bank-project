package com.banco.co.account.controller;

import com.banco.co.account.dto.AccountResponseDto;
import com.banco.co.account.enums.AccountStatus;
import com.banco.co.account.service.IAccountService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Validated
@RestController
@RequestMapping("/api/v1/admin/accounts")
public class AccountAdminController {

    private final IAccountService accountService;

    public AccountAdminController(IAccountService accountService) {
        this.accountService = accountService;
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasAnyAuthority('account:write', 'SCOPE_account:write') and hasAnyRole('SYSTEM_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<AccountResponseDto> updateStatus(
            @PathVariable UUID id,
            @RequestParam AccountStatus status,
            Authentication authentication) {
        return ResponseEntity.ok(accountService.updateAccountStatus(id, status, authentication.getName()));
    }

    @PostMapping("/{id}/close")
    @PreAuthorize("hasAnyAuthority('account:write', 'SCOPE_account:write') and hasAnyRole('SYSTEM_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<Void> closeByAdmin(
            @PathVariable UUID id,
            Authentication authentication) {
        accountService.closeAccountByAdmin(id, authentication.getName());
        return ResponseEntity.noContent().build();
    }
}
