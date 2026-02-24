package com.banco.co.security.codeGenerator;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.Random;

public class CodeGenerator {


    private static final Random random = new Random();
    private static final Random RANDOM = new SecureRandom();

    public static String generateWithPrefix(String PREFIX,int length) {
        int year = LocalDate.now().getYear();
        String randomPart = generateRandomNumeric(length);
        return String.format("%s-%d-%s", PREFIX, year, randomPart);
    }

    public static String generateRandomNumeric(int LENGTH) {
        StringBuilder sb = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) {
            sb.append(RANDOM.nextInt(10));
        }
        return sb.toString();
    }

    public static String generateWithChars(int LENGTH,String PREFIX) {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        StringBuilder sb = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) {
            sb.append(chars.charAt(RANDOM.nextInt(chars.length())));
        }
        return String.format("%s-%d-%s", PREFIX ,LocalDate.now().getYear(), sb.toString());
    }
}
