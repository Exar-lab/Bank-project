package com.banco.co.card.generator;

import com.banco.co.card.enums.CardBrand;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CardSecurityCodeGeneratorTest {

    @Test
    void testGenerateFor_WhenBrandIsAmex_ReturnsFourNumericDigits() {
        String securityCode = CardSecurityCodeGenerator.generateFor(CardBrand.AMEX);

        assertThat(securityCode).matches("\\d{4}");
    }

    @Test
    void testGenerateFor_WhenBrandIsVisa_ReturnsThreeNumericDigits() {
        String securityCode = CardSecurityCodeGenerator.generateFor(CardBrand.VISA);

        assertThat(securityCode).matches("\\d{3}");
    }
}
