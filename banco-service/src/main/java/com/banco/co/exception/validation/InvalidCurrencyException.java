package com.banco.co.exception.validation;

import org.springframework.http.HttpStatus;

public class InvalidCurrencyException extends ValidationException {

    private static final String ERROR_CODE = "INVALID_CURRENCY";
    private static final HttpStatus STATUS = HttpStatus.BAD_REQUEST;

    public InvalidCurrencyException(String currency) {
        super(
                String.format("Invalid or unsupported currency: %s", currency),
                ERROR_CODE,
                STATUS);
        this.addMetadata("currency", currency);
    }

    public InvalidCurrencyException(String currency, String supportedCurrencies) {
        super(
                String.format("Invalid currency '%s'. Supported currencies: %s", currency, supportedCurrencies),
                ERROR_CODE,
                STATUS);
        this.addMetadata("currency", currency);
        this.addMetadata("supportedCurrencies", supportedCurrencies);
    }
}
