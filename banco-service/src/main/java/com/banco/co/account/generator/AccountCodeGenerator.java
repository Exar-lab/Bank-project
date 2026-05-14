package com.banco.co.account.generator;

import java.security.SecureRandom;
import java.time.LocalDate;

public final class AccountCodeGenerator {

    private static final String PREFIX = "CR";
    private static final int NUMERIC_LENGTH = 20;
    private static final SecureRandom RANDOM = new SecureRandom();

    private AccountCodeGenerator() {
    }

    public static String generate() {
        return String.format("%s-%d-%s", PREFIX, LocalDate.now().getYear(), generateNumeric(NUMERIC_LENGTH));
    }

    private static String generateNumeric(int length) {
        StringBuilder value = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            value.append(RANDOM.nextInt(10));
        }
        return value.toString();
    }
}
