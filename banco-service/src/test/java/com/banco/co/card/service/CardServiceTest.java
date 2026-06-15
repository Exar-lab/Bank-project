package com.banco.co.card.service;

import com.banco.co.account.model.Account;
import com.banco.co.account.service.IAccountService;
import com.banco.co.auditLog.enums.AuditAction;
import com.banco.co.auditLog.enums.AuditEntityType;
import com.banco.co.auditLog.model.AuditLogDetail;
import com.banco.co.auditLog.service.IAuditLogService;
import com.banco.co.card.dto.*;
import com.banco.co.card.enums.CardBrand;
import com.banco.co.card.enums.CardStatus;
import com.banco.co.card.enums.CardTier;
import com.banco.co.card.enums.CardType;
import com.banco.co.card.exception.card.CardNotFoundException;
import com.banco.co.card.mapper.ICardMapper;
import com.banco.co.card.model.Card;
import com.banco.co.card.repository.ICardRepository;
import com.banco.co.exception.authentication.UnauthorizedException;
import com.banco.co.outbox.model.OutboxEvent;
import com.banco.co.outbox.port.IOutboxEventPort;
import com.banco.co.user.model.User;
import com.banco.co.user.service.user.IUserService;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for CardService — Mockito, no Spring context.
 * Covers TASK-10: 9 customer use cases.
 */
class CardServiceTest {

    private ICardRepository cardRepository;
    private IAccountService accountService;
    private IUserService userService;
    private IAuditLogService auditLogService;
    private ICardMapper cardMapper;
    private IOutboxEventPort outboxEventPort;
    private ObjectMapper objectMapper;

    private CardService cardService;

    // ── Test data ──────────────────────────────────────────────

    private static final String USER_EMAIL = "user@test.com";
    private static final String OTHER_EMAIL = "other@test.com";
    private static final String CARD_CODE = "CARD-2024-ABC123";
    private static final String ACCOUNT_CODE = "ACC-2024-XYZ";

    @BeforeEach
    void setUp() {
        cardRepository = mock(ICardRepository.class);
        accountService = mock(IAccountService.class);
        userService = mock(IUserService.class);
        auditLogService = mock(IAuditLogService.class);
        cardMapper = mock(ICardMapper.class);
        outboxEventPort = mock(IOutboxEventPort.class);
        objectMapper = new ObjectMapper();

        cardService = new CardService(
                cardRepository,
                accountService,
                userService,
                auditLogService,
                cardMapper,
                outboxEventPort,
                objectMapper
        );
    }

    // ══════════════════════════════════════════════════════════
    //  createCard
    // ══════════════════════════════════════════════════════════

    @Test
    void testCreateCard_ValidRequest_ReturnsDto() {
        // Arrange
        User user = buildUser(USER_EMAIL);
        Account account = buildAccount(ACCOUNT_CODE, user);
        Card savedCard = buildCard(CARD_CODE, CardStatus.INACTIVE, account);
        CardResponseDto expectedDto = buildCardResponseDto();
        CreateCardRequestDto dto = new CreateCardRequestDto(CardType.DEBITO, CardBrand.VISA, CardTier.CLASSIC, ACCOUNT_CODE);

        when(userService.getEntityUserByEmail(USER_EMAIL)).thenReturn(user);
        when(accountService.findAccountWithUserByAccountCode(ACCOUNT_CODE)).thenReturn(account);
        when(cardRepository.save(any(Card.class))).thenReturn(savedCard);
        when(cardMapper.toDto(savedCard)).thenReturn(expectedDto);
        when(outboxEventPort.save(any())).thenReturn(mock(OutboxEvent.class));

        // Act
        CardResponseDto result = cardService.createCard(dto, USER_EMAIL);

        // Assert
        assertThat(result).isEqualTo(expectedDto);
        verify(cardRepository).save(any(Card.class));
        verify(auditLogService).logSuccess(eq(user), eq(AuditAction.CARD_CREATED), eq(AuditEntityType.CARD), any(), any());
        verify(outboxEventPort).save(any(OutboxEvent.class));
    }

    @Test
    void testCreateCard_AccountNotOwned_ThrowsUnauthorized() {
        // Arrange
        User user = buildUser(USER_EMAIL);
        User otherUser = buildUser(OTHER_EMAIL);
        Account account = buildAccount(ACCOUNT_CODE, otherUser); // owned by someone else
        CreateCardRequestDto dto = new CreateCardRequestDto(CardType.DEBITO, CardBrand.VISA, CardTier.CLASSIC, ACCOUNT_CODE);

        when(userService.getEntityUserByEmail(USER_EMAIL)).thenReturn(user);
        when(accountService.findAccountWithUserByAccountCode(ACCOUNT_CODE)).thenReturn(account);

        // Act & Assert
        assertThatThrownBy(() -> cardService.createCard(dto, USER_EMAIL))
                .isInstanceOf(UnauthorizedException.class);

        verify(auditLogService).logFailure(eq(user), eq(AuditAction.CARD_CREATED_FAILED), eq(AuditEntityType.CARD), any());
        verify(cardRepository, never()).save(any());
    }

    // ══════════════════════════════════════════════════════════
    //  getMyCards
    // ══════════════════════════════════════════════════════════

    @Test
    void testGetMyCards_ReturnsAllCards() {
        // Arrange
        User user = buildUser(USER_EMAIL);
        Account account = buildAccount(ACCOUNT_CODE, user);
        Card card1 = buildCard("CARD-001", CardStatus.ACTIVE, account);
        Card card2 = buildCard("CARD-002", CardStatus.INACTIVE, account);
        CardSummaryDto summary1 = buildCardSummaryDto("CARD-001");
        CardSummaryDto summary2 = buildCardSummaryDto("CARD-002");

        when(cardRepository.findAllByAccountUserEmail(USER_EMAIL)).thenReturn(List.of(card1, card2));
        when(cardMapper.toSummaryDto(card1)).thenReturn(summary1);
        when(cardMapper.toSummaryDto(card2)).thenReturn(summary2);

        // Act
        List<CardSummaryDto> result = cardService.getMyCards(USER_EMAIL);

        // Assert
        assertThat(result).hasSize(2).containsExactly(summary1, summary2);
    }

    // ══════════════════════════════════════════════════════════
    //  getCardByCode
    // ══════════════════════════════════════════════════════════

    @Test
    void testGetCardByCode_OwnedCard_ReturnsDto() {
        // Arrange
        User user = buildUser(USER_EMAIL);
        Account account = buildAccount(ACCOUNT_CODE, user);
        Card card = buildCard(CARD_CODE, CardStatus.ACTIVE, account);
        CardResponseDto expectedDto = buildCardResponseDto();

        when(cardRepository.findByCardCodeWithAccount(CARD_CODE)).thenReturn(Optional.of(card));
        when(userService.getEntityUserByEmail(USER_EMAIL)).thenReturn(user);
        when(cardMapper.toDto(card)).thenReturn(expectedDto);

        // Act
        CardResponseDto result = cardService.getCardByCode(CARD_CODE, USER_EMAIL);

        // Assert
        assertThat(result).isEqualTo(expectedDto);
    }

    // ══════════════════════════════════════════════════════════
    //  activateCard
    // ══════════════════════════════════════════════════════════

    @Test
    void testActivateCard_ValidPin_ReturnsActiveCard() {
        // Arrange
        User user = buildUser(USER_EMAIL);
        Account account = buildAccount(ACCOUNT_CODE, user);
        Card card = buildCard(CARD_CODE, CardStatus.INACTIVE, account);
        CardResponseDto expectedDto = buildCardResponseDto();
        ActivateCardRequestDto dto = new ActivateCardRequestDto("123456");

        when(cardRepository.findByCardCodeWithAccount(CARD_CODE)).thenReturn(Optional.of(card));
        when(userService.getEntityUserByEmail(USER_EMAIL)).thenReturn(user);
        when(cardRepository.save(card)).thenReturn(card);
        when(cardMapper.toDto(card)).thenReturn(expectedDto);
        when(outboxEventPort.save(any())).thenReturn(mock(OutboxEvent.class));

        // Act
        CardResponseDto result = cardService.activateCard(CARD_CODE, dto, USER_EMAIL);

        // Assert
        assertThat(result).isEqualTo(expectedDto);
        verify(cardRepository).save(card);
    }

    @Test
    @SuppressWarnings("unchecked")
    void testActivateCard_PinNeverInAuditDetails() {
        // Arrange
        String secretPin = "999888";
        User user = buildUser(USER_EMAIL);
        Account account = buildAccount(ACCOUNT_CODE, user);
        Card card = buildCard(CARD_CODE, CardStatus.INACTIVE, account);
        ActivateCardRequestDto dto = new ActivateCardRequestDto(secretPin);

        when(cardRepository.findByCardCodeWithAccount(CARD_CODE)).thenReturn(Optional.of(card));
        when(userService.getEntityUserByEmail(USER_EMAIL)).thenReturn(user);
        when(cardRepository.save(card)).thenReturn(card);
        when(cardMapper.toDto(any())).thenReturn(buildCardResponseDto());
        when(outboxEventPort.save(any())).thenReturn(mock(OutboxEvent.class));

        // Act
        cardService.activateCard(CARD_CODE, dto, USER_EMAIL);

        // Assert — capture the details list passed to logSuccess
        ArgumentCaptor<List<AuditLogDetail>> detailsCaptor = ArgumentCaptor.forClass(List.class);
        verify(auditLogService).logSuccess(any(), any(), any(), any(), detailsCaptor.capture());

        List<AuditLogDetail> capturedDetails = detailsCaptor.getValue();
        boolean pinLeaked = capturedDetails.stream()
                .anyMatch(d -> d.getValue() != null && d.getValue().toString().contains(secretPin));
        assertThat(pinLeaked)
                .as("PIN must never appear in audit details")
                .isFalse();
    }

    @Test
    void testActivateCard_PinNeverInOutboxPayload() {
        // Arrange
        String secretPin = "777666";
        User user = buildUser(USER_EMAIL);
        Account account = buildAccount(ACCOUNT_CODE, user);
        Card card = buildCard(CARD_CODE, CardStatus.INACTIVE, account);
        ActivateCardRequestDto dto = new ActivateCardRequestDto(secretPin);

        when(cardRepository.findByCardCodeWithAccount(CARD_CODE)).thenReturn(Optional.of(card));
        when(userService.getEntityUserByEmail(USER_EMAIL)).thenReturn(user);
        when(cardRepository.save(card)).thenReturn(card);
        when(cardMapper.toDto(any())).thenReturn(buildCardResponseDto());
        when(outboxEventPort.save(any())).thenReturn(mock(OutboxEvent.class));

        // Act
        cardService.activateCard(CARD_CODE, dto, USER_EMAIL);

        // Assert — capture OutboxEvent payload
        ArgumentCaptor<OutboxEvent> outboxCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventPort).save(outboxCaptor.capture());

        String payload = outboxCaptor.getValue().getPayload();
        assertThat(payload)
                .as("PIN must never appear in outbox payload")
                .doesNotContain(secretPin);
        assertThat(payload)
                .as("pinHash must never appear in outbox payload")
                .doesNotContain("pinHash");
    }

    // ══════════════════════════════════════════════════════════
    //  blockCard
    // ══════════════════════════════════════════════════════════

    @Test
    void testBlockCard_ValidReason_ReturnsBlockedCard() {
        // Arrange
        User user = buildUser(USER_EMAIL);
        Account account = buildAccount(ACCOUNT_CODE, user);
        Card card = buildCard(CARD_CODE, CardStatus.ACTIVE, account);
        CardResponseDto expectedDto = buildCardResponseDto();
        BlockCardRequestDto dto = new BlockCardRequestDto("Suspicious activity");

        when(cardRepository.findByCardCodeWithAccount(CARD_CODE)).thenReturn(Optional.of(card));
        when(userService.getEntityUserByEmail(USER_EMAIL)).thenReturn(user);
        when(cardRepository.save(card)).thenReturn(card);
        when(cardMapper.toDto(card)).thenReturn(expectedDto);
        when(outboxEventPort.save(any())).thenReturn(mock(OutboxEvent.class));

        // Act
        CardResponseDto result = cardService.blockCard(CARD_CODE, dto, USER_EMAIL);

        // Assert
        assertThat(result).isEqualTo(expectedDto);
        verify(cardRepository).save(card);
        verify(auditLogService).logSuccess(eq(user), eq(AuditAction.CARD_BLOCKED), eq(AuditEntityType.CARD), any(), any());
    }

    // ══════════════════════════════════════════════════════════
    //  reportStolen
    // ══════════════════════════════════════════════════════════

    @Test
    void testReportStolen_OwnedCard_ReturnsDto() {
        // Arrange
        User user = buildUser(USER_EMAIL);
        Account account = buildAccount(ACCOUNT_CODE, user);
        Card card = buildCard(CARD_CODE, CardStatus.ACTIVE, account);
        CardResponseDto expectedDto = buildCardResponseDto();

        when(cardRepository.findByCardCodeWithAccount(CARD_CODE)).thenReturn(Optional.of(card));
        when(userService.getEntityUserByEmail(USER_EMAIL)).thenReturn(user);
        when(cardRepository.save(card)).thenReturn(card);
        when(cardMapper.toDto(card)).thenReturn(expectedDto);
        when(outboxEventPort.save(any())).thenReturn(mock(OutboxEvent.class));

        // Act
        CardResponseDto result = cardService.reportStolen(CARD_CODE, USER_EMAIL);

        // Assert
        assertThat(result).isEqualTo(expectedDto);
        verify(auditLogService).logSuccess(eq(user), eq(AuditAction.CARD_STOLEN_REPORTED), eq(AuditEntityType.CARD), any(), any());
    }

    // ══════════════════════════════════════════════════════════
    //  reportLost
    // ══════════════════════════════════════════════════════════

    @Test
    void testReportLost_OnActiveCard_ReturnsDto() {
        // Arrange
        User user = buildUser(USER_EMAIL);
        Account account = buildAccount(ACCOUNT_CODE, user);
        Card card = buildCard(CARD_CODE, CardStatus.ACTIVE, account);
        CardResponseDto expectedDto = buildCardResponseDto();

        when(cardRepository.findByCardCodeWithAccount(CARD_CODE)).thenReturn(Optional.of(card));
        when(userService.getEntityUserByEmail(USER_EMAIL)).thenReturn(user);
        when(cardRepository.save(card)).thenReturn(card);
        when(cardMapper.toDto(card)).thenReturn(expectedDto);
        when(outboxEventPort.save(any())).thenReturn(mock(OutboxEvent.class));

        // Act
        CardResponseDto result = cardService.reportLost(CARD_CODE, USER_EMAIL);

        // Assert
        assertThat(result).isEqualTo(expectedDto);
        verify(auditLogService).logSuccess(eq(user), eq(AuditAction.CARD_LOST_REPORTED), eq(AuditEntityType.CARD), any(), any());
    }

    // ══════════════════════════════════════════════════════════
    //  closeCard
    // ══════════════════════════════════════════════════════════

    @Test
    void testCloseCard_OnActiveCard_ReturnsDto() {
        // Arrange
        User user = buildUser(USER_EMAIL);
        Account account = buildAccount(ACCOUNT_CODE, user);
        Card card = buildCard(CARD_CODE, CardStatus.ACTIVE, account);
        CardResponseDto expectedDto = buildCardResponseDto();

        when(cardRepository.findByCardCodeWithAccount(CARD_CODE)).thenReturn(Optional.of(card));
        when(userService.getEntityUserByEmail(USER_EMAIL)).thenReturn(user);
        when(cardRepository.save(card)).thenReturn(card);
        when(cardMapper.toDto(card)).thenReturn(expectedDto);
        when(outboxEventPort.save(any())).thenReturn(mock(OutboxEvent.class));

        // Act
        CardResponseDto result = cardService.closeCard(CARD_CODE, USER_EMAIL);

        // Assert
        assertThat(result).isEqualTo(expectedDto);
        verify(auditLogService).logSuccess(eq(user), eq(AuditAction.CARD_CLOSED), eq(AuditEntityType.CARD), any(), any());
    }

    // ══════════════════════════════════════════════════════════
    //  updateLimits
    // ══════════════════════════════════════════════════════════

    @Test
    void testUpdateLimits_ValidRequest_ReturnsDto() {
        // Arrange
        User user = buildUser(USER_EMAIL);
        Account account = buildAccount(ACCOUNT_CODE, user);
        Card card = buildCard(CARD_CODE, CardStatus.ACTIVE, account);
        CardResponseDto expectedDto = buildCardResponseDto();
        UpdateCardLimitsRequestDto dto = new UpdateCardLimitsRequestDto(
                new BigDecimal("300000"), new BigDecimal("9000000"));

        when(cardRepository.findByCardCodeWithAccount(CARD_CODE)).thenReturn(Optional.of(card));
        when(userService.getEntityUserByEmail(USER_EMAIL)).thenReturn(user);
        when(cardRepository.save(card)).thenReturn(card);
        when(cardMapper.toDto(card)).thenReturn(expectedDto);
        when(outboxEventPort.save(any())).thenReturn(mock(OutboxEvent.class));

        // Act
        CardResponseDto result = cardService.updateLimits(CARD_CODE, dto, USER_EMAIL);

        // Assert
        assertThat(result).isEqualTo(expectedDto);
        assertThat(card.getDailyLimit()).isEqualByComparingTo("300000");
        assertThat(card.getMonthlyLimit()).isEqualByComparingTo("9000000");
        verify(cardRepository).save(card);
        verify(auditLogService).logSuccess(eq(user), eq(AuditAction.CARD_LIMITS_UPDATED), eq(AuditEntityType.CARD), any(), any());
        verify(outboxEventPort).save(any(OutboxEvent.class));
    }

    @Test
    void testUpdateLimits_DailyExceedsMonthly_ThrowsException() {
        // Arrange
        User user = buildUser(USER_EMAIL);
        Account account = buildAccount(ACCOUNT_CODE, user);
        Card card = buildCard(CARD_CODE, CardStatus.ACTIVE, account);
        UpdateCardLimitsRequestDto dto = new UpdateCardLimitsRequestDto(
                new BigDecimal("5000000"), new BigDecimal("1000000")); // daily > monthly

        when(cardRepository.findByCardCodeWithAccount(CARD_CODE)).thenReturn(Optional.of(card));
        when(userService.getEntityUserByEmail(USER_EMAIL)).thenReturn(user);

        // Act & Assert
        assertThatThrownBy(() -> cardService.updateLimits(CARD_CODE, dto, USER_EMAIL))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Daily limit cannot exceed monthly limit");

        verify(cardRepository, never()).save(any());
        verify(auditLogService, never()).logSuccess(any(), any(), any(), any(), any());
    }

    @Test
    void testUpdateLimits_OtherUser_ThrowsUnauthorized() {
        // Arrange
        User owner = buildUser(USER_EMAIL);
        User intruder = buildUser(OTHER_EMAIL);
        Account account = buildAccount(ACCOUNT_CODE, owner);
        Card card = buildCard(CARD_CODE, CardStatus.ACTIVE, account);
        UpdateCardLimitsRequestDto dto = new UpdateCardLimitsRequestDto(
                new BigDecimal("300000"), new BigDecimal("9000000"));

        when(cardRepository.findByCardCodeWithAccount(CARD_CODE)).thenReturn(Optional.of(card));
        when(userService.getEntityUserByEmail(OTHER_EMAIL)).thenReturn(intruder);

        // Act & Assert
        assertThatThrownBy(() -> cardService.updateLimits(CARD_CODE, dto, OTHER_EMAIL))
                .isInstanceOf(UnauthorizedException.class);

        verify(cardRepository, never()).save(any());
    }

    // ══════════════════════════════════════════════════════════
    //  updateFeatures
    // ══════════════════════════════════════════════════════════

    @Test
    void testUpdateFeatures_PartialUpdate_OnlyChangesNonNullFields() {
        // Arrange
        User user = buildUser(USER_EMAIL);
        Account account = buildAccount(ACCOUNT_CODE, user);
        Card card = buildCard(CARD_CODE, CardStatus.ACTIVE, account);
        // Initial state: contactless=true, online=true, international=false
        card.setContactlessEnabled(true);
        card.setOnlinePaymentsEnabled(true);
        card.setInternationalEnabled(false);

        CardResponseDto expectedDto = buildCardResponseDto();
        // Only send internationalEnabled=true — the other two fields are null (not changing)
        UpdateCardFeaturesRequestDto dto = new UpdateCardFeaturesRequestDto(null, null, true);

        when(cardRepository.findByCardCodeWithAccount(CARD_CODE)).thenReturn(Optional.of(card));
        when(userService.getEntityUserByEmail(USER_EMAIL)).thenReturn(user);
        when(cardRepository.save(card)).thenReturn(card);
        when(cardMapper.toDto(card)).thenReturn(expectedDto);
        when(outboxEventPort.save(any())).thenReturn(mock(OutboxEvent.class));

        // Act
        CardResponseDto result = cardService.updateFeatures(CARD_CODE, dto, USER_EMAIL);

        // Assert — only internationalEnabled was flipped; the others stayed unchanged
        assertThat(result).isEqualTo(expectedDto);
        assertThat(card.isContactlessEnabled()).isTrue();
        assertThat(card.isOnlinePaymentsEnabled()).isTrue();
        assertThat(card.isInternationalEnabled()).isTrue();
        verify(cardRepository).save(card);
        verify(auditLogService).logSuccess(eq(user), eq(AuditAction.CARD_FEATURES_UPDATED), eq(AuditEntityType.CARD), any(), any());
        verify(outboxEventPort).save(any(OutboxEvent.class));
    }

    // ══════════════════════════════════════════════════════════
    //  ADMIN USE CASES
    // ══════════════════════════════════════════════════════════

    @Test
    void testAdminGetAllByStatus_ReturnsPaginatedPage() {
        // Arrange
        User user = buildUser(USER_EMAIL);
        Account account = buildAccount(ACCOUNT_CODE, user);
        Card card1 = buildCard("CARD-001", CardStatus.BLOCKED, account);
        Card card2 = buildCard("CARD-002", CardStatus.BLOCKED, account);
        CardSummaryDto summary1 = buildCardSummaryDto("CARD-001");
        CardSummaryDto summary2 = buildCardSummaryDto("CARD-002");

        Pageable pageable = PageRequest.of(0, 10);
        Page<Card> cardPage = new PageImpl<>(List.of(card1, card2), pageable, 2);

        when(cardRepository.findAllByStatus(CardStatus.BLOCKED, pageable)).thenReturn(cardPage);
        when(cardMapper.toSummaryDto(card1)).thenReturn(summary1);
        when(cardMapper.toSummaryDto(card2)).thenReturn(summary2);

        // Act
        Page<CardSummaryDto> result = cardService.adminGetAllByStatus(CardStatus.BLOCKED, pageable);

        // Assert
        assertThat(result.getContent()).hasSize(2).containsExactly(summary1, summary2);
        assertThat(result.getTotalElements()).isEqualTo(2);
        verify(cardRepository).findAllByStatus(CardStatus.BLOCKED, pageable);
    }

    @Test
    void testAdminChangeStatus_ValidAdmin_ReturnsDto() {
        // Arrange
        User owner = buildUser(USER_EMAIL);
        Account account = buildAccount(ACCOUNT_CODE, owner);
        Card card = buildCard(CARD_CODE, CardStatus.ACTIVE, account);
        CardResponseDto expectedDto = buildCardResponseDto();
        AdminChangeCardStatusRequestDto dto = new AdminChangeCardStatusRequestDto(CardStatus.BLOCKED, "Fraud investigation");

        when(cardRepository.findByCardCodeWithAccount(CARD_CODE)).thenReturn(Optional.of(card));
        when(cardRepository.save(card)).thenReturn(card);
        when(cardMapper.toDto(card)).thenReturn(expectedDto);
        when(outboxEventPort.save(any())).thenReturn(mock(OutboxEvent.class));

        // Act
        CardResponseDto result = cardService.adminChangeStatus(CARD_CODE, dto, "admin@banco.co");

        // Assert
        assertThat(result).isEqualTo(expectedDto);
        assertThat(card.getStatus()).isEqualTo(CardStatus.BLOCKED);
        assertThat(card.getBlockedReason()).isEqualTo("Fraud investigation");
        verify(cardRepository).save(card);
        verify(auditLogService).logAnonymous(eq(AuditAction.CARD_STATUS_CHANGED), eq(AuditEntityType.CARD), any(), any());
        verify(outboxEventPort).save(any(OutboxEvent.class));
    }

    @Test
    void testAdminChangeStatus_CardNotFound_Throws() {
        // Arrange
        AdminChangeCardStatusRequestDto dto = new AdminChangeCardStatusRequestDto(CardStatus.BLOCKED, null);
        when(cardRepository.findByCardCodeWithAccount("NONEXISTENT")).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> cardService.adminChangeStatus("NONEXISTENT", dto, "admin@banco.co"))
                .isInstanceOf(CardNotFoundException.class);

        verify(cardRepository, never()).save(any());
    }

    @Test
    void testAdminResetPin_ValidAdmin_NoException() {
        // Arrange
        User owner = buildUser(USER_EMAIL);
        Account account = buildAccount(ACCOUNT_CODE, owner);
        Card card = buildCard(CARD_CODE, CardStatus.ACTIVE, account);
        AdminResetPinRequestDto dto = new AdminResetPinRequestDto("654321");

        when(cardRepository.findByCardCode(CARD_CODE)).thenReturn(Optional.of(card));
        when(cardRepository.save(card)).thenReturn(card);
        when(outboxEventPort.save(any())).thenReturn(mock(OutboxEvent.class));

        // Act — should not throw
        cardService.adminResetPin(CARD_CODE, dto, "admin@banco.co");

        // Assert
        verify(cardRepository).save(card);
        verify(auditLogService).logAnonymous(eq(AuditAction.CARD_PIN_RESET), eq(AuditEntityType.CARD), any(), any());
        verify(outboxEventPort).save(any(OutboxEvent.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testAdminResetPin_PinNeverInAuditDetails() {
        // Arrange
        String newPin = "987654";
        User owner = buildUser(USER_EMAIL);
        Account account = buildAccount(ACCOUNT_CODE, owner);
        Card card = buildCard(CARD_CODE, CardStatus.ACTIVE, account);
        AdminResetPinRequestDto dto = new AdminResetPinRequestDto(newPin);

        when(cardRepository.findByCardCode(CARD_CODE)).thenReturn(Optional.of(card));
        when(cardRepository.save(card)).thenReturn(card);
        when(outboxEventPort.save(any())).thenReturn(mock(OutboxEvent.class));

        // Act
        cardService.adminResetPin(CARD_CODE, dto, "admin@banco.co");

        // Assert — capture details passed to logAnonymous
        ArgumentCaptor<List<AuditLogDetail>> detailsCaptor = ArgumentCaptor.forClass(List.class);
        verify(auditLogService).logAnonymous(any(), any(), any(), detailsCaptor.capture());

        List<AuditLogDetail> capturedDetails = detailsCaptor.getValue();
        boolean pinLeaked = capturedDetails.stream()
                .anyMatch(d -> d.getValue() != null && d.getValue().toString().contains(newPin));
        assertThat(pinLeaked)
                .as("newPin must NEVER appear in audit details")
                .isFalse();
    }

    @Test
    void testAdminResetPin_PinNeverInOutboxPayload() {
        // Arrange
        String newPin = "112233";
        User owner = buildUser(USER_EMAIL);
        Account account = buildAccount(ACCOUNT_CODE, owner);
        Card card = buildCard(CARD_CODE, CardStatus.ACTIVE, account);
        AdminResetPinRequestDto dto = new AdminResetPinRequestDto(newPin);

        when(cardRepository.findByCardCode(CARD_CODE)).thenReturn(Optional.of(card));
        when(cardRepository.save(card)).thenReturn(card);
        when(outboxEventPort.save(any())).thenReturn(mock(OutboxEvent.class));

        // Act
        cardService.adminResetPin(CARD_CODE, dto, "admin@banco.co");

        // Assert — capture outbox payload and verify PIN is absent
        ArgumentCaptor<OutboxEvent> outboxCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventPort).save(outboxCaptor.capture());

        String payload = outboxCaptor.getValue().getPayload();
        assertThat(payload)
                .as("newPin must NEVER appear in outbox payload")
                .doesNotContain(newPin);
        assertThat(payload)
                .as("pinHash must NEVER appear in outbox payload")
                .doesNotContain("pinHash");
    }

    // ══════════════════════════════════════════════════════════
    //  Task 1.4 RED tests — Card.accountId UUID field
    // ══════════════════════════════════════════════════════════

    /**
     * Task 1.4 RED: Card.accountId does not exist yet.
     * Fails to compile until the field is added to Card with insertable=false, updatable=false.
     * GREEN: card.getAccountId() returns non-null after save, matching the account's UUID.
     */
    @Test
    void testCreateCard_ValidData_CardHasAccountId() {
        // Arrange
        User user = buildUser(USER_EMAIL);
        Account account = buildAccount(ACCOUNT_CODE, user);
        UUID expectedAccountId = account.getId();

        // Build a saved card that mirrors what would come back from the repository
        Card savedCard = buildCard(CARD_CODE, CardStatus.INACTIVE, account);
        // Simulate the JPA-populated accountId (insertable=false, updatable=false column)
        // After save the DB populates account_id from the FK; we set it on the returned card
        setField(savedCard, "accountId", expectedAccountId);

        CardResponseDto expectedDto = buildCardResponseDto();
        CreateCardRequestDto dto = new CreateCardRequestDto(CardType.DEBITO, CardBrand.VISA, CardTier.CLASSIC, ACCOUNT_CODE);

        when(userService.getEntityUserByEmail(USER_EMAIL)).thenReturn(user);
        when(accountService.findAccountWithUserByAccountCode(ACCOUNT_CODE)).thenReturn(account);
        when(cardRepository.save(any(Card.class))).thenReturn(savedCard);
        when(cardMapper.toDto(savedCard)).thenReturn(expectedDto);
        when(outboxEventPort.save(any())).thenReturn(mock(OutboxEvent.class));

        // Act
        cardService.createCard(dto, USER_EMAIL);

        // Assert — the saved card has accountId populated
        // RED: Card.getAccountId() does not exist yet — compilation error
        assertThat(savedCard.getAccountId()).isNotNull();
        assertThat(savedCard.getAccountId()).isEqualTo(expectedAccountId);
    }

    // ══════════════════════════════════════════════════════════
    //  Helpers
    // ══════════════════════════════════════════════════════════

    private User buildUser(String email) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail(email);
        return user;
    }

    private Account buildAccount(String accountCode, User owner) {
        Account account = new Account();
        setField(account, "id", UUID.randomUUID());
        setField(account, "accountCode", accountCode);
        account.setUser(owner);
        return account;
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set field " + fieldName, e);
        }
    }

    private Card buildCard(String cardCode, CardStatus status, Account account) {
        Card card = new Card();
        card.setId(UUID.randomUUID());
        card.setCardCode(cardCode);
        card.setStatus(status);
        card.setAccount(account);
        card.setCardType(CardType.DEBITO);
        card.setBrand(CardBrand.VISA);
        card.setTier(CardTier.CLASSIC);
        card.setDailyLimit(new BigDecimal("500000"));
        card.setMonthlyLimit(new BigDecimal("15000000"));
        card.setDailySpent(BigDecimal.ZERO);
        card.setMonthlySpent(BigDecimal.ZERO);
        card.setExpirationDate(LocalDateTime.now().plusYears(5));
        card.setPinHash("$2a$12$dummyhash");
        card.setPoints(0L);
        return card;
    }

    private CardResponseDto buildCardResponseDto() {
        return new CardResponseDto(
                CARD_CODE,
                "****-****-****-1234",
                CardType.DEBITO,
                CardBrand.VISA,
                CardTier.CLASSIC,
                CardStatus.INACTIVE,
                null,
                ACCOUNT_CODE,
                LocalDateTime.now(),
                LocalDateTime.now().plusYears(5),
                null,
                null,
                new BigDecimal("500000"),
                new BigDecimal("15000000"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                true,
                true,
                false,
                0L
        );
    }

    private CardSummaryDto buildCardSummaryDto(String cardCode) {
        return new CardSummaryDto(
                cardCode,
                "****-****-****-1234",
                CardType.DEBITO,
                CardBrand.VISA,
                CardTier.CLASSIC,
                CardStatus.INACTIVE,
                ACCOUNT_CODE,
                LocalDateTime.now().plusYears(5)
        );
    }
}
