package com.banco.co.transaction.exception.transaction;

import org.springframework.http.HttpStatus;

public class TransactionInvalidException extends TransactionException {

    // Usamos una convención de nomenclatura estándar (Mayúsculas con guiones bajos)
    private static final String ERROR_CODE = "TRANSACTION_INVALID_OPERATION";
    private static final HttpStatus STATUS = HttpStatus.BAD_REQUEST;

    /**
     * @param accountCode El código de la cuenta afectada.
     * @param reason La razón humana/de negocio del fallo (ej: "Monto excede el límite diario").
     */
    public TransactionInvalidException(String accountCode, String reason) {
        super(
                String.format("The transaction for account %s could not be processed: %s", accountCode, reason),
                ERROR_CODE,
                STATUS
        );

        // Los metadatos permiten que el Frontend no tenga que "leer" el String del mensaje
        addMetadata("accountCode", accountCode);
        addMetadata("rejectionReason", reason);
    }

    // Constructor para excepciones con causa (ej: un error de base de datos o API externa)
    public TransactionInvalidException(String message, String errorCode, HttpStatus httpStatus, Throwable cause) {
        super(message, errorCode, httpStatus, cause);
    }
}