package com.banco.co.transaction.controller;

import com.banco.co.transaction.dto.CategorySummaryDto;
import com.banco.co.transaction.dto.ScheduledTransferRequestDto;
import com.banco.co.transaction.dto.TransactionFiltersDto;
import com.banco.co.transaction.dto.TransactionRequestMetadataDto;
import com.banco.co.transaction.dto.TransactionResponseDto;
import com.banco.co.transaction.dto.movement.TransferRequestDto;
import com.banco.co.transaction.dto.payment.PaymentRequestDto;
import com.banco.co.transaction.dto.payment.ServicePaymentRequestDto;
import com.banco.co.transaction.enums.TransactionCategory;
import com.banco.co.transaction.service.ITransactionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Validated
@RestController
@RequestMapping("/api/v1/transactions")
public class TransactionController {

    private final ITransactionService transactionService;

    public TransactionController(ITransactionService transactionService) {
        this.transactionService = transactionService;
    }

    // ══════════════════════════════════════════════════════════
    //  OPERACIONES DIGITALES
    // ══════════════════════════════════════════════════════════

    @PostMapping("/transfer")
    @PreAuthorize("hasAuthority('transaction:create') or hasAnyRole('CUSTOMER_BASIC', 'CUSTOMER_PREMIUM')")
    public ResponseEntity<TransactionResponseDto> transfer(
            @Valid @RequestBody TransferRequestDto dto,
            Authentication authentication,
            HttpServletRequest request
    ) {
        TransactionRequestMetadataDto metadata = TransactionMetadataExtractor.extract(request);
        TransactionResponseDto response = transactionService.transfer(dto, authentication.getName(), metadata);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/payment")
    @PreAuthorize("hasAuthority('transaction:create') or hasAnyRole('CUSTOMER_BASIC', 'CUSTOMER_PREMIUM')")
    public ResponseEntity<TransactionResponseDto> payment(
            @Valid @RequestBody PaymentRequestDto dto,
            Authentication authentication,
            HttpServletRequest request
    ) {
        TransactionRequestMetadataDto metadata = TransactionMetadataExtractor.extract(request);
        TransactionResponseDto response = transactionService.payment(dto, authentication.getName(), metadata);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/pay-service")
    @PreAuthorize("hasAuthority('transaction:create') or hasAnyRole('CUSTOMER_BASIC', 'CUSTOMER_PREMIUM')")
    public ResponseEntity<TransactionResponseDto> payService(
            @Valid @RequestBody ServicePaymentRequestDto dto,
            Authentication authentication,
            HttpServletRequest request
    ) {
        TransactionRequestMetadataDto metadata = TransactionMetadataExtractor.extract(request);
        TransactionResponseDto response = transactionService.payService(dto, authentication.getName(), metadata);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/schedule")
    @PreAuthorize("hasAuthority('transaction:create') or hasAnyRole('CUSTOMER_BASIC', 'CUSTOMER_PREMIUM')")
    public ResponseEntity<TransactionResponseDto> scheduleTransfer(
            @Valid @RequestBody ScheduledTransferRequestDto dto,
            Authentication authentication,
            HttpServletRequest request
    ) {
        TransactionRequestMetadataDto metadata = TransactionMetadataExtractor.extract(request);
        TransactionResponseDto response = transactionService.scheduleTransfer(dto, authentication.getName(), metadata);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @DeleteMapping("/{id}/schedule")
    @PreAuthorize("hasAuthority('transaction:reverse') or hasAnyRole('CUSTOMER_BASIC', 'CUSTOMER_PREMIUM')")
    public ResponseEntity<Void> cancelScheduledTransaction(
            @PathVariable UUID id,
            Authentication authentication
    ) {
        transactionService.cancelScheduledTransaction(id, authentication.getName());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/reversal")
    @PreAuthorize("hasAuthority('transaction:reverse') or hasAnyRole('CUSTOMER_BASIC', 'CUSTOMER_PREMIUM')")
    public ResponseEntity<Void> requestReversal(
            @PathVariable UUID id,
            @RequestParam String reason,
            Authentication authentication
    ) {
        transactionService.requestReversal(id, reason, authentication.getName());
        return ResponseEntity.accepted().build();
    }

    // ══════════════════════════════════════════════════════════
    //  CONSULTAS
    // ══════════════════════════════════════════════════════════

    @GetMapping("/me")
    @PreAuthorize("hasAuthority('transaction:read') or hasAnyRole('CUSTOMER_BASIC', 'CUSTOMER_PREMIUM')")
    public ResponseEntity<Page<TransactionResponseDto>> getMyTransactions(
            @ModelAttribute TransactionFiltersDto filters,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            Authentication authentication
    ) {
        Page<TransactionResponseDto> page = transactionService.getMyTransactions(
                authentication.getName(), filters, pageable);
        return ResponseEntity.ok(page);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('transaction:read') or hasAnyRole('CUSTOMER_BASIC', 'CUSTOMER_PREMIUM')")
    public ResponseEntity<TransactionResponseDto> getMyTransaction(
            @PathVariable UUID id,
            Authentication authentication
    ) {
        TransactionResponseDto response = transactionService.getMyTransaction(id, authentication.getName());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/account/{code}")
    @PreAuthorize("hasAuthority('transaction:read') or hasAnyRole('CUSTOMER_BASIC', 'CUSTOMER_PREMIUM')")
    public ResponseEntity<Page<TransactionResponseDto>> getAccountTransactions(
            @PathVariable String code,
            @ModelAttribute TransactionFiltersDto filters,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            Authentication authentication
    ) {
        Page<TransactionResponseDto> page = transactionService.getAccountTransactions(
                code, authentication.getName(), filters, pageable);
        return ResponseEntity.ok(page);
    }

    @GetMapping("/categories")
    @PreAuthorize("hasAuthority('transaction:read') or hasAnyRole('CUSTOMER_BASIC', 'CUSTOMER_PREMIUM')")
    public ResponseEntity<List<TransactionResponseDto>> getTransactionsByCategory(
            @RequestParam TransactionCategory category,
            Authentication authentication
    ) {
        List<TransactionResponseDto> response = transactionService.getTransactionsByCategory(
                category, authentication.getName());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/summary")
    @PreAuthorize("hasAuthority('transaction:read') or hasAnyRole('CUSTOMER_BASIC', 'CUSTOMER_PREMIUM')")
    public ResponseEntity<CategorySummaryDto> getCategorySummary(
            @RequestParam(required = false) LocalDateTime startDate,
            @RequestParam(required = false) LocalDateTime endDate,
            Authentication authentication
    ) {
        CategorySummaryDto response = transactionService.getCategorySummary(
                authentication.getName(), startDate, endDate);
        return ResponseEntity.ok(response);
    }

}
