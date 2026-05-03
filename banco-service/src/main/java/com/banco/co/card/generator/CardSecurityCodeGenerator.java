package com.banco.co.card.generator;

import com.banco.co.card.enums.CardBrand;

import java.security.SecureRandom;

public final class CardSecurityCodeGenerator {

    private static final int AMEX_SECURITY_CODE_LENGTH = 4;
    private static final int DEFAULT_SECURITY_CODE_LENGTH = 3;
    private static final SecureRandom RANDOM = new SecureRandom();

    private CardSecurityCodeGenerator() {
    }

    public static String generateFor(CardBrand brand) {
        int length = brand == CardBrand.AMEX ? AMEX_SECURITY_CODE_LENGTH : DEFAULT_SECURITY_CODE_LENGTH;
        StringBuilder value = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            value.append(RANDOM.nextInt(10));
        }
        return value.toString();
    }
}
