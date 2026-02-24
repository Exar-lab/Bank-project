package com.banco.co.Bank.exception;

import com.banco.co.exception.validation.ValidationException;
import org.springframework.http.HttpStatus;

public class CountryCodeNotFoundException extends ValidationException {

    private static final String ERROR_CODE = "COUNTRY_CODE_NOT_FOUND";
    private static final HttpStatus STATUS = HttpStatus.NOT_FOUND;

    public CountryCodeNotFoundException(String input) {
        super(
                String.format("No country code found for: '%s'", input),
                ERROR_CODE,
                STATUS);
        this.addMetadata("input", input);
    }
}
