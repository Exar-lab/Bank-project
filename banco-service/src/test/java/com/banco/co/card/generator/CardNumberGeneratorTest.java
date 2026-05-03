package com.banco.co.card.generator;

import com.banco.co.card.enums.CardBrand;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CardNumberGeneratorTest {

    @Test
    void testGenerateValid_WhenBrandIsVisa_ReturnsLuhnValidSixteenDigitNumber() {
        String cardNumber = CardNumberGenerator.generateValid(CardBrand.VISA);

        assertThat(cardNumber).matches("4\\d{15}");
        assertThat(CardNumberGenerator.isValidLuhn(cardNumber)).isTrue();
    }

    @Test
    void testGenerateValid_WhenBrandIsAmex_ReturnsLuhnValidFifteenDigitNumber() {
        String cardNumber = CardNumberGenerator.generateValid(CardBrand.AMEX);

        assertThat(cardNumber).matches("37\\d{13}");
        assertThat(CardNumberGenerator.isValidLuhn(cardNumber)).isTrue();
    }
}
