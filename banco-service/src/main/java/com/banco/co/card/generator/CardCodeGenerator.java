package com.banco.co.card.generator;

import java.security.SecureRandom;
import java.time.LocalDate;

public final class CardCodeGenerator {

    private static final String PREFIX = "CARD";
    private static final String SAFE_ALPHANUMERIC = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int RANDOM_LENGTH = 6;
    private static final SecureRandom RANDOM = new SecureRandom();

    private CardCodeGenerator() {
    }

    public static String generate() {
        StringBuilder randomPart = new StringBuilder(RANDOM_LENGTH);
        for (int i = 0; i < RANDOM_LENGTH; i++) {
            randomPart.append(SAFE_ALPHANUMERIC.charAt(RANDOM.nextInt(SAFE_ALPHANUMERIC.length())));
        }
        return String.format("%s-%d-%s", PREFIX, LocalDate.now().getYear(), randomPart);
    }
}
