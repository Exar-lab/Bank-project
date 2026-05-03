package com.banco.co.account.generator;

import java.security.SecureRandom;

public final class AccountNumberGenerator {

    private static final int NUMERIC_LENGTH = 12;
    private static final SecureRandom RANDOM = new SecureRandom();

    private AccountNumberGenerator() {
    }

    public static String generate() {
        StringBuilder value = new StringBuilder(NUMERIC_LENGTH);
        for (int i = 0; i < NUMERIC_LENGTH; i++) {
            value.append(RANDOM.nextInt(10));
        }
        return value.toString();
    }
}
