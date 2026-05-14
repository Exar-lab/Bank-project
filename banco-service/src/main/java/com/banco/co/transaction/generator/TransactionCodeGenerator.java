package com.banco.co.transaction.generator;

import java.security.SecureRandom;

public class TransactionCodeGenerator {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int RANDOM_NUMERIC_LENGTH = 8;

    public static String generate() {
        String timestamp = String.valueOf(System.currentTimeMillis());
        String randomPart = generateRandomNumeric(RANDOM_NUMERIC_LENGTH);
        return String.format("TXN-%s-%s",
                timestamp.substring(timestamp.length() - 8),
                randomPart
        );
    }

    private static String generateRandomNumeric(int length) {
        StringBuilder value = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            value.append(RANDOM.nextInt(10));
        }
        return value.toString();
    }
}
