package com.banco.co.Bank.exception;

import com.banco.co.exception.validation.ValidationException;
import org.springframework.http.HttpStatus;

public class InvalidCountryInputException extends ValidationException {

    private static final String ERROR_CODE = "INVALID_COUNTRY_INPUT";
    private static final HttpStatus STATUS = HttpStatus.BAD_REQUEST;

    public InvalidCountryInputException(String fieldName) {
        super(
                String.format("Country input '%s' cannot be null or empty", fieldName),
                ERROR_CODE,
                STATUS);
        this.addMetadata("field", fieldName);
    }
}
