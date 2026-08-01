package com.banco.co.card.model;

import com.banco.co.card.enums.CardBrand;
import com.banco.co.card.enums.CardStatus;
import com.banco.co.card.exception.card.CardClosedException;
import com.banco.co.card.exception.card.CardException;
import com.banco.co.security.cryptoLib.BlindIndexHasher;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pure domain tests — no Spring context, no mocks.
 * Covers close() and reportLost() state transitions (SCEN-D-001 through SCEN-D-008).
 */
class CardTest {

    // ══════════════════════════════════════════════════════════
    //  close()
    // ══════════════════════════════════════════════════════════

    @Test
    void testClose_WhenActive_SetsStatusToClosed() {
        // SCEN-D-001
        Card card = buildCard(CardStatus.ACTIVE);

        card.close();

        assertThat(card.getStatus()).isEqualTo(CardStatus.CLOSED);
    }

    @Test
    void testClose_WhenActive_SetsBlockedReason() {
        // SCEN-D-001
        Card card = buildCard(CardStatus.ACTIVE);

        card.close();

        assertThat(card.getBlockedReason()).isEqualTo("Closed by cardholder");
    }

    @Test
    void testClose_WhenBlocked_SetsStatusToClosed() {
        // SCEN-D-002
        Card card = buildCard(CardStatus.BLOCKED);

        card.close();

        assertThat(card.getStatus()).isEqualTo(CardStatus.CLOSED);
    }

    @Test
    void testClose_WhenInactive_SetsStatusToClosed() {
        // SCEN-D-003
        Card card = buildCard(CardStatus.INACTIVE);

        card.close();

        assertThat(card.getStatus()).isEqualTo(CardStatus.CLOSED);
    }

    @Test
    void testClose_WhenAlreadyClosed_ThrowsCardClosedException() {
        // SCEN-D-004
        Card card = buildCard(CardStatus.CLOSED);

        assertThatThrownBy(card::close)
                .isInstanceOf(CardClosedException.class);
    }

    // ══════════════════════════════════════════════════════════
    //  reportLost()
    // ══════════════════════════════════════════════════════════

    @Test
    void testReportLost_WhenActive_SetsStatusToLost() {
        // SCEN-D-005
        Card card = buildCard(CardStatus.ACTIVE);

        card.reportLost();

        assertThat(card.getStatus()).isEqualTo(CardStatus.LOST);
    }

    @Test
    void testReportLost_WhenActive_SetsBlockedReason() {
        // SCEN-D-005
        Card card = buildCard(CardStatus.ACTIVE);

        card.reportLost();

        assertThat(card.getBlockedReason()).isEqualTo("Reported as lost by cardholder");
    }

    @Test
    void testReportLost_WhenInactive_SetsStatusToLost() {
        // SCEN-D-006
        Card card = buildCard(CardStatus.INACTIVE);

        card.reportLost();

        assertThat(card.getStatus()).isEqualTo(CardStatus.LOST);
    }

    @Test
    void testReportLost_WhenClosed_ThrowsCardException() {
        // SCEN-D-007
        Card card = buildCard(CardStatus.CLOSED);

        assertThatThrownBy(card::reportLost)
                .isInstanceOf(CardException.class);
    }

    @Test
    void testReportLost_WhenStolen_ThrowsCardException() {
        // SCEN-D-008
        Card card = buildCard(CardStatus.STOLEN);

        assertThatThrownBy(card::reportLost)
                .isInstanceOf(CardException.class);
    }

    // ══════════════════════════════════════════════════════════
    //  generateCardData() — cardNumberHash blind index
    // ══════════════════════════════════════════════════════════

    @Test
    void testGenerateCardData_WhenCardNumberHashUnset_ComputesHashFromCardNumber() {
        Card card = buildCard(CardStatus.INACTIVE);
        card.setBrand(CardBrand.VISA);
        card.setTier(com.banco.co.card.enums.CardTier.CLASSIC);

        card.generateCardData();

        assertThat(card.getCardNumberHash())
                .isEqualTo(BlindIndexHasher.sha256Hex(card.getCardNumber()));
    }

    @Test
    void testGenerateCardData_WhenCardNumberHashAlreadySet_DoesNotOverwriteIt() {
        Card card = buildCard(CardStatus.INACTIVE);
        card.setBrand(CardBrand.VISA);
        card.setTier(com.banco.co.card.enums.CardTier.CLASSIC);
        card.setCardNumber("4111111111111111");
        card.setCardNumberHash("preexisting-hash");

        card.generateCardData();

        assertThat(card.getCardNumberHash()).isEqualTo("preexisting-hash");
    }

    // ══════════════════════════════════════════════════════════
    //  Helper
    // ══════════════════════════════════════════════════════════

    private Card buildCard(CardStatus status) {
        Card card = new Card();
        card.setStatus(status);
        card.setCardCode("CARD-TEST-001");
        return card;
    }
}
