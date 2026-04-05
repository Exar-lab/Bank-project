package com.banco.co.card.mapper;

import com.banco.co.account.model.Account;
import com.banco.co.card.dto.CardResponseDto;
import com.banco.co.card.dto.CardSummaryDto;
import com.banco.co.card.enums.CardBrand;
import com.banco.co.card.enums.CardStatus;
import com.banco.co.card.enums.CardTier;
import com.banco.co.card.enums.CardType;
import com.banco.co.card.model.Card;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pure mapper tests — no Spring context.
 * Uses MapStruct's generated impl via Mappers.getMapper().
 */
class ICardMapperTest {

    private ICardMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = Mappers.getMapper(ICardMapper.class);
    }

    // ══════════════════════════════════════════════════════════
    //  toDto
    // ══════════════════════════════════════════════════════════

    @Test
    void testToDto_MapsAllFields() throws Exception {
        Card card = buildCard("4532015112830366");

        CardResponseDto dto = mapper.toDto(card);

        assertThat(dto.cardCode()).isEqualTo(card.getCardCode());
        assertThat(dto.cardType()).isEqualTo(CardType.DEBITO);
        assertThat(dto.brand()).isEqualTo(CardBrand.VISA);
        assertThat(dto.tier()).isEqualTo(CardTier.GOLD);
        assertThat(dto.status()).isEqualTo(CardStatus.ACTIVE);
        assertThat(dto.blockedReason()).isNull();
        assertThat(dto.accountCode()).isEqualTo("ACC-001");
        assertThat(dto.dailyLimit()).isEqualByComparingTo(new BigDecimal("1000000"));
        assertThat(dto.monthlyLimit()).isEqualByComparingTo(new BigDecimal("30000000"));
        assertThat(dto.contactlessEnabled()).isTrue();
        assertThat(dto.onlinePaymentsEnabled()).isTrue();
        assertThat(dto.internationalEnabled()).isFalse();
        assertThat(dto.points()).isEqualTo(0L);
    }

    // ══════════════════════════════════════════════════════════
    //  maskCardNumber
    // ══════════════════════════════════════════════════════════

    @Test
    void testMaskCardNumber_16Digits_ReturnsMaskedWithLastFour() throws Exception {
        Card card = buildCard("4532015112830366");

        CardResponseDto dto = mapper.toDto(card);

        assertThat(dto.maskedCardNumber()).isEqualTo("****-****-****-0366");
    }

    @Test
    void testMaskCardNumber_NullNumber_ReturnsPlaceholder() throws Exception {
        Card card = buildCard(null);

        CardResponseDto dto = mapper.toDto(card);

        assertThat(dto.maskedCardNumber()).isEqualTo("****-****-****-????");
    }

    @Test
    void testToDto_NeverExposesRawCardNumber() throws Exception {
        String rawNumber = "4532015112830366";
        Card card = buildCard(rawNumber);

        CardResponseDto dto = mapper.toDto(card);

        assertThat(dto.maskedCardNumber()).doesNotContain(rawNumber);
    }

    // ══════════════════════════════════════════════════════════
    //  toSummaryDto
    // ══════════════════════════════════════════════════════════

    @Test
    void testToSummaryDto_MapsRequiredFields() throws Exception {
        Card card = buildCard("4532015112830366");

        CardSummaryDto dto = mapper.toSummaryDto(card);

        assertThat(dto.cardCode()).isEqualTo(card.getCardCode());
        assertThat(dto.maskedCardNumber()).isEqualTo("****-****-****-0366");
        assertThat(dto.cardType()).isEqualTo(CardType.DEBITO);
        assertThat(dto.brand()).isEqualTo(CardBrand.VISA);
        assertThat(dto.tier()).isEqualTo(CardTier.GOLD);
        assertThat(dto.status()).isEqualTo(CardStatus.ACTIVE);
        assertThat(dto.accountCode()).isEqualTo("ACC-001");
        assertThat(dto.expirationDate()).isNotNull();
    }

    // ══════════════════════════════════════════════════════════
    //  Helpers
    // ══════════════════════════════════════════════════════════

    private Card buildCard(String cardNumber) throws Exception {
        Account account = new Account();
        setField(account, "accountCode", "ACC-001");

        Card card = new Card();
        card.setCardCode("CARD-2024-X7K9P2");
        card.setCardNumber(cardNumber);
        card.setCardType(CardType.DEBITO);
        card.setBrand(CardBrand.VISA);
        card.setTier(CardTier.GOLD);
        card.setStatus(CardStatus.ACTIVE);
        card.setAccount(account);
        card.setExpirationDate(LocalDateTime.now().plusYears(6));
        card.setDailyLimit(new BigDecimal("1000000"));
        card.setMonthlyLimit(new BigDecimal("30000000"));
        card.setDailySpent(BigDecimal.ZERO);
        card.setMonthlySpent(BigDecimal.ZERO);
        card.setContactlessEnabled(true);
        card.setOnlinePaymentsEnabled(true);
        card.setInternationalEnabled(false);
        card.setPoints(0L);
        card.setCreatedAt(LocalDateTime.now());
        card.setUpdatedAt(LocalDateTime.now());
        return card;
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
