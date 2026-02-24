package com.banco.co.Bank.branch.generator;

import java.security.SecureRandom;

public class BranchCodeGenerator {
    private static final SecureRandom RANDOM = new SecureRandom();

    public static String generate(String bankAbbreviation, String city) {
        // Tomar primeras 2 letras de la ciudad
        String cityCode = city.replaceAll("[^A-Za-z]", "")
                .substring(0, Math.min(2, city.length()))
                .toUpperCase();

        // Generar número secuencial o aleatorio
        String sequential = String.format("%03d", RANDOM.nextInt(1000));

        return String.format("%s-%s-%s",
                bankAbbreviation,
                cityCode,
                sequential
        );
    }
}