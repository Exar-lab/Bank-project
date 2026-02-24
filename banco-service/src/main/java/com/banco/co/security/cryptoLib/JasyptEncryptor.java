package com.banco.co.security.cryptoLib;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.RequiredArgsConstructor;
import org.jasypt.encryption.StringEncryptor;
import org.springframework.stereotype.Component;


@Component
@Converter
@RequiredArgsConstructor
public class JasyptEncryptor implements AttributeConverter<String, String> {

    private final StringEncryptor stringEncryptor;

    @Override
    public String convertToDatabaseColumn(String number) {
        return number == null ? null : stringEncryptor.encrypt(number);
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        return dbData == null ? null : stringEncryptor.decrypt(dbData);
    }
}