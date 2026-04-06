package com.banco.co.account.controller;

import com.banco.co.account.dto.AccountRequestDto;
import com.banco.co.account.dto.AccountResponseDto;
import com.banco.co.account.dto.AccountUpdateDto;
import com.banco.co.account.service.IAccountService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Validated
@RestController
@RequestMapping("/api/v1/accounts")
public class AccountController {

    private final IAccountService accountService;

    public AccountController(IAccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('account:read', 'SCOPE_account:read') or hasAnyRole('CUSTOMER_BASIC', 'CUSTOMER_PREMIUM')")
    public ResponseEntity<List<AccountResponseDto>> getMyAccounts(Authentication authentication) {
        return ResponseEntity.ok(accountService.getMyAccounts(authentication.getName()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('account:read', 'SCOPE_account:read') or hasAnyRole('CUSTOMER_BASIC', 'CUSTOMER_PREMIUM')")
    public ResponseEntity<AccountResponseDto> getAccount(
            @PathVariable UUID id,
            Authentication authentication) {
        return ResponseEntity.ok(accountService.getAccount(id, authentication.getName()));
    }

    @GetMapping("/code/{code}")
    @PreAuthorize("hasAnyAuthority('account:read', 'SCOPE_account:read') or hasAnyRole('CUSTOMER_BASIC', 'CUSTOMER_PREMIUM')")
    public ResponseEntity<AccountResponseDto> getAccountByCode(
            @PathVariable String code,
            Authentication authentication) {
        return ResponseEntity.ok(accountService.getAccountByCode(code, authentication.getName()));
    }

    @GetMapping("/{id}/balance")
    @PreAuthorize("hasAnyAuthority('account:read', 'SCOPE_account:read') or hasAnyRole('CUSTOMER_BASIC', 'CUSTOMER_PREMIUM')")
    public ResponseEntity<BigDecimal> getUnassignedBalance(
            @PathVariable UUID id,
            Authentication authentication) {
        accountService.getAccount(id, authentication.getName());
        return ResponseEntity.ok(accountService.getUnassignedBalance(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('account:write', 'SCOPE_account:write')")
    public ResponseEntity<AccountResponseDto> createAccount(
            @Valid @RequestBody AccountRequestDto dto,
            Authentication authentication) {
        AccountResponseDto response = accountService.createAccount(dto, authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{code}")
    @PreAuthorize("hasAnyAuthority('account:write', 'SCOPE_account:write')")
    public ResponseEntity<AccountResponseDto> updateAccount(
            @PathVariable String code,
            @Valid @RequestBody AccountUpdateDto dto,
            Authentication authentication) {
        return ResponseEntity.ok(accountService.updateAccount(code, dto, authentication.getName()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('account:write', 'SCOPE_account:write')")
    public ResponseEntity<Void> closeAccount(
            @PathVariable UUID id,
            Authentication authentication) {
        accountService.closeAccount(id, authentication.getName());
        return ResponseEntity.noContent().build();
    }
}
