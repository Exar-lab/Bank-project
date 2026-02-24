package com.banco.co.Transaction.generator;

import com.banco.co.security.codeGenerator.CodeGenerator;

import java.security.SecureRandom;

public class TransactionCodeGenerator {
    private static final SecureRandom RANDOM = new SecureRandom();

    public static String generate() {
        String timestamp = String.valueOf(System.currentTimeMillis());
        String randomPart = CodeGenerator.generateRandomNumeric(8);
        return String.format("TXN-%s-%s",
                timestamp.substring(timestamp.length() - 8),
                randomPart
        );
    }
}