package com.banco.co.card.generator;

import com.banco.co.card.enums.CardBrand;

import java.security.SecureRandom;

public class CardNumberGenerator {
    private static final SecureRandom RANDOM = new SecureRandom();

    public static String generateValid(CardBrand brand) {
        String prefix = switch (brand) {
            case VISA -> "4";           // Visa empieza con 4
            case MASTERCARD -> "5";     // Mastercard empieza con 5
            case AMEX -> "37";          // Amex empieza con 34 o 37
        };

        int length = (brand == CardBrand.AMEX) ? 15 : 16;

        StringBuilder cardNumber = new StringBuilder(prefix);

        // Generar dígitos aleatorios excepto el último (checksum)
        while (cardNumber.length() < length - 1) {
            cardNumber.append(RANDOM.nextInt(10));
        }

        // Calcular y agregar dígito de control Luhn
        int checkDigit = calculateLuhnCheckDigit(cardNumber.toString());
        cardNumber.append(checkDigit);

        return cardNumber.toString();
    }

    private static int calculateLuhnCheckDigit(String number) {
        int sum = 0;
        boolean alternate = true;

        sum = getSum(number, sum, alternate);

        return (10 - (sum % 10)) % 10;
    }

    public static boolean isValidLuhn(String cardNumber) {
        int sum = 0;
        boolean alternate = false;

        sum = getSum(cardNumber, sum, alternate);

        return sum % 10 == 0;
    }

    private static int getSum(String cardNumber, int sum, boolean alternate) {
        for (int i = cardNumber.length() - 1; i >= 0; i--) {
            int digit = Character.getNumericValue(cardNumber.charAt(i));

            if (alternate) {
                digit *= 2;
                if (digit > 9) digit -= 9;
            }

            sum += digit;
            alternate = !alternate;
        }
        return sum;
    }
}