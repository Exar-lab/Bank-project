package com.banco.co.transaction.controller;

import com.banco.co.transaction.dto.TransactionRequestMetadataDto;
import com.banco.co.transaction.dto.TransactionResponseDto;
import com.banco.co.transaction.dto.movement.CashDepositRequestDto;
import com.banco.co.transaction.dto.movement.CashWithdrawalRequestDto;
import com.banco.co.transaction.dto.movement.CheckDepositRequestDto;
import com.banco.co.transaction.service.ITransactionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/api/v1/teller/transactions")
public class TransactionEmployeeController {

    private final ITransactionService transactionService;

    public TransactionEmployeeController(ITransactionService transactionService) {
        this.transactionService = transactionService;
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
        TransactionResponseDto response = transactionService.cashDeposit(dto, authentication.getName(), metadata);
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
        TransactionResponseDto response = transactionService.cashWithdrawal(dto, authentication.getName(), metadata);
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
        TransactionResponseDto response = transactionService.checkDeposit(dto, authentication.getName(), metadata);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

}
