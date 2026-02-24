package com.banco.co.user.generator;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.Random;

public class UserCodeGenerator {

    private static final String PREFIX = "USR";
    private static final Random RANDOM = new SecureRandom();
    private static final int LENGTH = 6;

    public static String generate() {
        int year = LocalDate.now().getYear();
        String randomPart = generateRandomAlphanumeric();
        return String.format("%s-%d-%s", PREFIX, year, randomPart);
    }

    private static String generateRandomAlphanumeric() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // Sin letras confusas
        StringBuilder sb = new StringBuilder(LENGTH);
        for (int i = 0; i < 6; i++) {
            sb.append(chars.charAt(RANDOM.nextInt(chars.length())));
        }
        return sb.toString();
    }
}