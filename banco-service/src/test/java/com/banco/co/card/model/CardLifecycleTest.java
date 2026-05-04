package com.banco.co.card.model;

import com.banco.co.card.enums.CardBrand;
import com.banco.co.card.enums.CardTier;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class CardLifecycleTest {

    @Test
    void testGenerateCardData_WhenGeneratedFieldsAreNull_FillsCardCodeCardNumberAndSecurityCode() {
        int currentYear = LocalDate.now().getYear();
        Card card = new Card();
        card.setBrand(CardBrand.VISA);
        card.setTier(CardTier.CLASSIC);

        card.generateCardData();

        assertThat(card.getCardCode())
                .matches("CARD-" + currentYear + "-[ABCDEFGHJKLMNPQRSTUVWXYZ23456789]{6}");
        assertThat(card.getCardNumber()).matches("4\\d{15}");
        assertThat(card.getSecurityCode()).matches("\\d{3}");
    }

    @Test
    void testGenerateCardData_WhenGeneratedFieldsAlreadyExist_PreservesExistingValues() {
        Card card = new Card();
        card.setBrand(CardBrand.AMEX);
        card.setTier(CardTier.CLASSIC);
        card.setCardCode("CARD-EXISTING-001");
        card.setCardNumber("4111111111111111");
        card.setSecurityCode("999");

        card.generateCardData();

        assertThat(card.getCardCode()).isEqualTo("CARD-EXISTING-001");
        assertThat(card.getCardNumber()).isEqualTo("4111111111111111");
        assertThat(card.getSecurityCode()).isEqualTo("999");
    }
}
