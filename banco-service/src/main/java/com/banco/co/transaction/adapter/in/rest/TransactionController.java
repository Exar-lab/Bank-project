package com.banco.co.transaction.adapter.in.rest;

import com.banco.co.transaction.domain.port.in.ITransactionUseCase;
import com.banco.co.transaction.dto.CategorySummaryDto;
import com.banco.co.transaction.dto.ScheduledTransferRequestDto;
import com.banco.co.transaction.dto.TransactionFiltersDto;
import com.banco.co.transaction.dto.TransactionRequestMetadataDto;
import com.banco.co.transaction.dto.TransactionResponseDto;
import com.banco.co.transaction.dto.movement.TransferRequestDto;
import com.banco.co.transaction.dto.payment.PaymentRequestDto;
import com.banco.co.transaction.dto.payment.ServicePaymentRequestDto;
import com.banco.co.transaction.enums.TransactionCategory;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Hexagonal adapter — REST input port for Transaction use cases.
 *
 * NOTE: @RestController is intentionally commented out to avoid duplicate URL
 * mapping with com.banco.co.transaction.controller.TransactionController during the
 * additive migration phase. Uncomment once the legacy controller is removed (Phase 6).
 *
 * Depends on ITransactionUseCase (domain input port) instead of legacy ITransactionService.
 */
@Validated
// @RestController   ← uncomment after removing legacy com.banco.co.transaction.controller.TransactionController
// @RequestMapping("/api/v1/transactions")
public class TransactionController {

    private final ITransactionUseCase transactionUseCase;

    public TransactionController(ITransactionUseCase transactionUseCase) {
        this.transactionUseCase = transactionUseCase;
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
        TransactionResponseDto response = transactionUseCase.transfer(dto, authentication.getName(), metadata);
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
        TransactionResponseDto response = transactionUseCase.payment(dto, authentication.getName(), metadata);
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
        TransactionResponseDto response = transactionUseCase.payService(dto, authentication.getName(), metadata);
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
        TransactionResponseDto response = transactionUseCase.scheduleTransfer(dto, authentication.getName(), metadata);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @DeleteMapping("/{id}/schedule")
    @PreAuthorize("hasAuthority('transaction:reverse') or hasAnyRole('CUSTOMER_BASIC', 'CUSTOMER_PREMIUM')")
    public ResponseEntity<Void> cancelScheduledTransaction(
            @PathVariable UUID id,
            Authentication authentication
    ) {
        transactionUseCase.cancelScheduledTransaction(id, authentication.getName());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/reversal")
    @PreAuthorize("hasAuthority('transaction:reverse') or hasAnyRole('CUSTOMER_BASIC', 'CUSTOMER_PREMIUM')")
    public ResponseEntity<Void> requestReversal(
            @PathVariable UUID id,
            @RequestParam String reason,
            Authentication authentication
    ) {
        transactionUseCase.requestReversal(id, reason, authentication.getName());
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
        Page<TransactionResponseDto> page = transactionUseCase.getMyTransactions(
                authentication.getName(), filters, pageable);
        return ResponseEntity.ok(page);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('transaction:read') or hasAnyRole('CUSTOMER_BASIC', 'CUSTOMER_PREMIUM')")
    public ResponseEntity<TransactionResponseDto> getMyTransaction(
            @PathVariable UUID id,
            Authentication authentication
    ) {
        TransactionResponseDto response = transactionUseCase.getMyTransaction(id, authentication.getName());
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
        Page<TransactionResponseDto> page = transactionUseCase.getAccountTransactions(
                code, authentication.getName(), filters, pageable);
        return ResponseEntity.ok(page);
    }

    @GetMapping("/categories")
    @PreAuthorize("hasAuthority('transaction:read') or hasAnyRole('CUSTOMER_BASIC', 'CUSTOMER_PREMIUM')")
    public ResponseEntity<List<TransactionResponseDto>> getTransactionsByCategory(
            @RequestParam TransactionCategory category,
            Authentication authentication
    ) {
        List<TransactionResponseDto> response = transactionUseCase.getTransactionsByCategory(
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
        CategorySummaryDto response = transactionUseCase.getCategorySummary(
                authentication.getName(), startDate, endDate);
        return ResponseEntity.ok(response);
    }
}
