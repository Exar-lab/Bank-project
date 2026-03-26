package com.banco.co.transaction.service;

import com.banco.co.transaction.dto.CategorySummaryDto;
import com.banco.co.transaction.dto.ScheduledTransferRequestDto;
import com.banco.co.transaction.dto.TransactionFiltersDto;
import com.banco.co.transaction.dto.TransactionResponseDto;
import com.banco.co.transaction.dto.movement.CashDepositRequestDto;
import com.banco.co.transaction.dto.movement.CashWithdrawalRequestDto;
import com.banco.co.transaction.dto.movement.CheckDepositRequestDto;
import com.banco.co.transaction.dto.movement.TransferRequestDto;
import com.banco.co.transaction.dto.payment.PaymentRequestDto;
import com.banco.co.transaction.dto.payment.ServicePaymentRequestDto;
import com.banco.co.transaction.enums.TransactionCategory;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.Page;

import org.springframework.data.domain.Pageable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface ITransactionService {
    // ══════════════════════════════════════════════════════════
    //  OPERACIONES DIGITALES (Usuario autenticado)
    // ══════════════════════════════════════════════════════════

    /**
     * Transferir entre cuentas (propia o a terceros)
     * Usuario debe ser dueño de la cuenta origen
     */
    TransactionResponseDto transfer(
            TransferRequestDto dto,
            String userEmail,
            HttpServletRequest request
    );

    /**
     * Pago con tarjeta de débito/crédito
     * Usuario debe ser dueño de la tarjeta
     */
    TransactionResponseDto payment(
            PaymentRequestDto dto,
            String userEmail,
            HttpServletRequest request
    );

    /**
     * Pago de servicio (luz, agua, teléfono, etc.)
     */
    TransactionResponseDto payService(
            ServicePaymentRequestDto dto,
            String userEmail,
            HttpServletRequest request
    );

    // ══════════════════════════════════════════════════════════
    //  OPERACIONES PRESENCIALES (Empleado)
    // ══════════════════════════════════════════════════════════

    /**
     * Depósito en efectivo (empleado procesa)
     * Empleado puede operar cualquier cuenta de cliente
     */
    TransactionResponseDto cashDeposit(
            CashDepositRequestDto dto,
            String employeeEmail,
            HttpServletRequest request
    );

    /**
     * Retiro en efectivo (empleado procesa)
     * Empleado puede operar cualquier cuenta de cliente
     */
    TransactionResponseDto cashWithdrawal(
            CashWithdrawalRequestDto dto,
            String employeeEmail,
            HttpServletRequest request
    );

    /**
     * Depósito con cheque (empleado procesa)
     */
    TransactionResponseDto checkDeposit(
            CheckDepositRequestDto dto,
            String employeeEmail,
            HttpServletRequest request
    );

    // ══════════════════════════════════════════════════════════
    //  CONSULTAS
    // ══════════════════════════════════════════════════════════

    /**
     * Obtener transacciones del usuario autenticado
     */
    Page<TransactionResponseDto> getMyTransactions(
            String userEmail,
            TransactionFiltersDto filters,
            Pageable pageable
    );

    /**
     * Obtener una transacción específica del usuario
     */
    TransactionResponseDto getMyTransaction(
            UUID transactionId,
            String userEmail
    );

    /**
     * Obtener transacciones de una cuenta específica
     */
    Page<TransactionResponseDto> getAccountTransactions(
            String accountCode,
            String userEmail,
            TransactionFiltersDto filters,
            Pageable pageable
    );

    /**
     * Obtener transacciones por categoría
     */
    List<TransactionResponseDto> getTransactionsByCategory(
            TransactionCategory category,
            String userEmail
    );

    /**
     * Obtener resumen de gastos por categoría
     */
    CategorySummaryDto getCategorySummary(
            String userEmail,
            LocalDateTime startDate,
            LocalDateTime endDate
    );

    // ══════════════════════════════════════════════════════════
    //  OPERACIONES ESPECIALES
    // ══════════════════════════════════════════════════════════

    /**
     * Programar transferencia futura
     */
    TransactionResponseDto scheduleTransfer(
            ScheduledTransferRequestDto dto,
            String userEmail,
            HttpServletRequest request
    );

    /**
     * Cancelar transacción programada
     */
    void cancelScheduledTransaction(UUID transactionId, String userEmail);

    /**
     * Solicitar reversión de transacción (usuario)
     */
    void requestReversal(UUID transactionId, String reason, String userEmail);

    // ══════════════════════════════════════════════════════════
    //  OPERACIONES ADMINISTRATIVAS
    // ══════════════════════════════════════════════════════════

    /**
     * Admin/Fraud Analyst obtiene TODAS las transacciones
     */
    Page<TransactionResponseDto> getAllTransactions(
            TransactionFiltersDto filters,
            Pageable pageable,
            String adminEmail
    );

    /**
     * Admin/Fraud Analyst obtiene transacciones sospechosas
     */
    List<TransactionResponseDto> getSuspiciousTransactions(String analystEmail);

    /**
     * Admin aprueba transacción en revisión
     */
    TransactionResponseDto approveTransaction(
            UUID transactionId,
            String adminEmail
    );

    /**
     * Admin rechaza transacción
     */
    void rejectTransaction(
            UUID transactionId,
            String reason,
            String adminEmail
    );

    /**
     * Admin revierte transacción
     */
    TransactionResponseDto reverseTransaction(
            UUID transactionId,
            String reason,
            String adminEmail
    );

    /**
     * Marcar transacción como fraudulenta
     */
    void flagAsFraud(
            UUID transactionId,
            BigDecimal fraudScore,
            String reason,
            String analystEmail
    );
}
