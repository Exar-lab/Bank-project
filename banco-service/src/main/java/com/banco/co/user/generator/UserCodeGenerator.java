package com.banco.co.user.generator;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.Random;

public class UserCodeGenerator {

    private static final String PREFIX = "USR";
    private static final Random RANDOM = new SecureRandom();
    private static final int LENGTH = 6;
    private static final int USERNAME_NUMERIC_LENGTH = 10;

    public static String generate() {
        int year = LocalDate.now().getYear();
        String randomPart = generateRandomAlphanumeric();
        return String.format("%s-%d-%s", PREFIX, year, randomPart);
    }

    public static String generateUsername(String firstName) {
        int year = LocalDate.now().getYear();
        String randomPart = generateRandomNumeric(USERNAME_NUMERIC_LENGTH);
        return String.format("%s-%d-%s", firstName, year, randomPart);
    }

    private static String generateRandomAlphanumeric() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // Sin letras confusas
        StringBuilder sb = new StringBuilder(LENGTH);
        for (int i = 0; i < 6; i++) {
            sb.append(chars.charAt(RANDOM.nextInt(chars.length())));
        }
        return sb.toString();
    }

    private static String generateRandomNumeric(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(RANDOM.nextInt(10));
        }
        return sb.toString();
    }
}
