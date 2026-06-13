package com.banco.co.transaction.adapter.in.rest;

import com.banco.co.transaction.domain.port.in.ITransactionUseCase;
import com.banco.co.transaction.dto.TransactionRequestMetadataDto;
import com.banco.co.transaction.dto.TransactionResponseDto;
import com.banco.co.transaction.dto.movement.CashDepositRequestDto;
import com.banco.co.transaction.dto.movement.CashWithdrawalRequestDto;
import com.banco.co.transaction.dto.movement.CheckDepositRequestDto;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Hexagonal adapter — REST input port for teller/employee Transaction use cases.
 *
 * NOTE: @RestController is intentionally commented out to avoid duplicate URL
 * mapping with com.banco.co.transaction.controller.TransactionEmployeeController during the
 * additive migration phase. Uncomment once the legacy controller is removed (Phase 6).
 */
@Validated
// @RestController   ← uncomment after removing legacy com.banco.co.transaction.controller.TransactionEmployeeController
// @RequestMapping("/api/v1/teller/transactions")
public class TransactionEmployeeController {

    private final ITransactionUseCase transactionUseCase;

    public TransactionEmployeeController(ITransactionUseCase transactionUseCase) {
        this.transactionUseCase = transactionUseCase;
    }

    // ══════════════════════════════════════════════════════════
    //  OPERACIONES PRESENCIALES (Cajero / Teller)
    // ══════════════════════════════════════════════════════════

    @PostMapping("/cash-deposit")
    @PreAuthorize("hasAuthority('transaction:create') and hasAnyRole('TELLER', 'ADVISOR', 'BRANCH_MANAGER')")
    public ResponseEntity<TransactionResponseDto> cashDeposit(
            @Valid @RequestBody CashDepositRequestDto dto,
            Authentication authentication,
            HttpServletRequest request
    ) {
        TransactionRequestMetadataDto metadata = TransactionMetadataExtractor.extract(request);
        TransactionResponseDto response = transactionUseCase.cashDeposit(dto, authentication.getName(), metadata);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/cash-withdrawal")
    @PreAuthorize("hasAuthority('transaction:create') and hasAnyRole('TELLER', 'ADVISOR', 'BRANCH_MANAGER')")
    public ResponseEntity<TransactionResponseDto> cashWithdrawal(
            @Valid @RequestBody CashWithdrawalRequestDto dto,
            Authentication authentication,
            HttpServletRequest request
    ) {
        TransactionRequestMetadataDto metadata = TransactionMetadataExtractor.extract(request);
        TransactionResponseDto response = transactionUseCase.cashWithdrawal(dto, authentication.getName(), metadata);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/check-deposit")
    @PreAuthorize("hasAuthority('transaction:create') and hasAnyRole('TELLER', 'ADVISOR', 'BRANCH_MANAGER')")
    public ResponseEntity<TransactionResponseDto> checkDeposit(
            @Valid @RequestBody CheckDepositRequestDto dto,
            Authentication authentication,
            HttpServletRequest request
    ) {
        TransactionRequestMetadataDto metadata = TransactionMetadataExtractor.extract(request);
        TransactionResponseDto response = transactionUseCase.checkDeposit(dto, authentication.getName(), metadata);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
