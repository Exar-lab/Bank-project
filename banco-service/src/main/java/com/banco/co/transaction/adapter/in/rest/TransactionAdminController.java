package com.banco.co.transaction.adapter.in.rest;

import com.banco.co.transaction.domain.port.in.ITransactionUseCase;
import com.banco.co.transaction.dto.FraudFlagRequestDto;
import com.banco.co.transaction.dto.TransactionFiltersDto;
import com.banco.co.transaction.dto.TransactionResponseDto;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Hexagonal adapter — REST input port for admin Transaction use cases.
 *
 * NOTE: @RestController is intentionally commented out to avoid duplicate URL
 * mapping with com.banco.co.transaction.controller.TransactionAdminController during the
 * additive migration phase. Uncomment once the legacy controller is removed (Phase 6).
 */
@Validated
// @RestController   ← uncomment after removing legacy com.banco.co.transaction.controller.TransactionAdminController
// @RequestMapping("/api/v1/admin/transactions")
public class TransactionAdminController {

    private final ITransactionUseCase transactionUseCase;

    public TransactionAdminController(ITransactionUseCase transactionUseCase) {
        this.transactionUseCase = transactionUseCase;
    }

    // ══════════════════════════════════════════════════════════
    //  CONSULTAS ADMIN / ANALISTA
    // ══════════════════════════════════════════════════════════

    @GetMapping
    @PreAuthorize("hasAuthority('transaction:read:all') or hasAnyRole('SYSTEM_ADMIN', 'SUPER_ADMIN', 'FRAUD_ANALYST', 'AUDITOR')")
    public ResponseEntity<Page<TransactionResponseDto>> getAllTransactions(
            @ModelAttribute TransactionFiltersDto filters,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            Authentication authentication
    ) {
        Page<TransactionResponseDto> page = transactionUseCase.getAllTransactions(
                filters, pageable, authentication.getName());
        return ResponseEntity.ok(page);
    }

    @GetMapping("/suspicious")
    @PreAuthorize("hasAuthority('fraud:alert:read') or hasAnyRole('FRAUD_ANALYST', 'SYSTEM_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<List<TransactionResponseDto>> getSuspiciousTransactions(
            Authentication authentication
    ) {
        List<TransactionResponseDto> response = transactionUseCase.getSuspiciousTransactions(
                authentication.getName());
        return ResponseEntity.ok(response);
    }

    // ══════════════════════════════════════════════════════════
    //  ACCIONES ADMIN
    // ══════════════════════════════════════════════════════════

    @PutMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('transaction:approve') or hasAnyRole('SYSTEM_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<TransactionResponseDto> approveTransaction(
            @PathVariable UUID id,
            Authentication authentication
    ) {
        TransactionResponseDto response = transactionUseCase.approveTransaction(id, authentication.getName());
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}/reject")
    @PreAuthorize("hasAuthority('transaction:reverse') and hasAnyRole('SYSTEM_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<Void> rejectTransaction(
            @PathVariable UUID id,
            @RequestParam String reason,
            Authentication authentication
    ) {
        transactionUseCase.rejectTransaction(id, reason, authentication.getName());
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/reverse")
    @PreAuthorize("hasAuthority('transaction:reverse') and hasAnyRole('SYSTEM_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<TransactionResponseDto> reverseTransaction(
            @PathVariable UUID id,
            @RequestParam String reason,
            Authentication authentication
    ) {
        TransactionResponseDto response = transactionUseCase.reverseTransaction(id, reason, authentication.getName());
        return ResponseEntity.ok(response);
    }

    // ══════════════════════════════════════════════════════════
    //  ACCIONES ANALISTA DE FRAUDE
    // ══════════════════════════════════════════════════════════

    @PostMapping("/{id}/fraud")
    @PreAuthorize("hasAuthority('fraud:alert:resolve') or hasAnyRole('FRAUD_ANALYST', 'SYSTEM_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<Void> flagAsFraud(
            @PathVariable UUID id,
            @Valid @RequestBody FraudFlagRequestDto dto,
            Authentication authentication
    ) {
        transactionUseCase.flagAsFraud(id, dto.fraudScore(), dto.reason(), authentication.getName());
        return ResponseEntity.noContent().build();
    }
}
