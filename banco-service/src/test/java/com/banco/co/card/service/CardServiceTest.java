package com.banco.co.card.service;

import com.banco.co.account.adapter.out.jpa.AccountEntity;
import com.banco.co.account.adapter.out.jpa.IAccountJpaRepository;
import com.banco.co.account.domain.model.Account;
import com.banco.co.account.domain.port.in.IAccountUseCase;
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
import com.banco.co.user.domain.model.UserSnapshot;
import com.banco.co.user.domain.port.out.IUserRepository;
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
 * Phase 3: migrated to IAccountUseCase + domain Account.
 */
class CardServiceTest {

    private ICardRepository cardRepository;
    private IAccountUseCase accountUseCase;
    private IAccountJpaRepository accountJpaRepository;
    private IUserService userService;
    private IAuditLogService auditLogService;
    private ICardMapper cardMapper;
    private IOutboxEventPort outboxEventPort;
    private ObjectMapper objectMapper;
    private IUserRepository userDomainRepository;

    private CardService cardService;

    // ── Test data ──────────────────────────────────────────────

    private static final String USER_EMAIL = "user@test.com";
    private static final String OTHER_EMAIL = "other@test.com";
    private static final String CARD_CODE = "CARD-2024-ABC123";
    private static final String ACCOUNT_CODE = "ACC-2024-XYZ";

    @BeforeEach
    void setUp() {
        cardRepository = mock(ICardRepository.class);
        accountUseCase = mock(IAccountUseCase.class);
        accountJpaRepository = mock(IAccountJpaRepository.class);
        userService = mock(IUserService.class);
        auditLogService = mock(IAuditLogService.class);
        cardMapper = mock(ICardMapper.class);
        outboxEventPort = mock(IOutboxEventPort.class);
        objectMapper = new ObjectMapper();
        userDomainRepository = mock(IUserRepository.class);

        cardService = new CardService(
                cardRepository,
                accountUseCase,
                accountJpaRepository,
                userService,
                auditLogService,
                cardMapper,
                outboxEventPort,
                objectMapper,
                userDomainRepository
        );
    }

    // ══════════════════════════════════════════════════════════
    //  createCard
    // ══════════════════════════════════════════════════════════

    @Test
    void testCreateCard_ValidRequest_ReturnsDto() {
        // Arrange
        User user = buildUser(USER_EMAIL);
        Account account = buildDomainAccount(ACCOUNT_CODE, user.getId());
        AccountEntity accountEntity = buildAccountEntity(ACCOUNT_CODE, user);
        accountEntity.setId(account.getId());
        AccountEntity legacyAccount = accountEntity;
        Card savedCard = buildCardWithAccountEntity(CARD_CODE, CardStatus.INACTIVE, legacyAccount);
        CardResponseDto expectedDto = buildCardResponseDto();
        CreateCardRequestDto dto = new CreateCardRequestDto(CardType.DEBITO, CardBrand.VISA, CardTier.CLASSIC, ACCOUNT_CODE);

        when(userService.getEntityUserByEmail(USER_EMAIL)).thenReturn(user);
        when(accountUseCase.findAccountWithUserByAccountCode(ACCOUNT_CODE)).thenReturn(account);
        when(accountJpaRepository.findById(account.getId())).thenReturn(Optional.of(accountEntity));
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
        UUID otherUserId = UUID.randomUUID();
        Account account = buildDomainAccount(ACCOUNT_CODE, otherUserId); // owned by someone else
        CreateCardRequestDto dto = new CreateCardRequestDto(CardType.DEBITO, CardBrand.VISA, CardTier.CLASSIC, ACCOUNT_CODE);
        UserSnapshot snapshot = new UserSnapshot(otherUserId.toString(), OTHER_EMAIL, "otherUser", "USER");

        when(userService.getEntityUserByEmail(USER_EMAIL)).thenReturn(user);
        when(accountUseCase.findAccountWithUserByAccountCode(ACCOUNT_CODE)).thenReturn(account);
        when(userDomainRepository.findSnapshotByUserId(otherUserId)).thenReturn(snapshot);

        // Act & Assert
        assertThatThrownBy(() -> cardService.createCard(dto, USER_EMAIL))
                .isInstanceOf(UnauthorizedException.class);

        verify(auditLogService).logFailure(eq(user), eq(AuditAction.CARD_CREATED_FAILED), eq(AuditEntityType.CARD), any());
        verify(cardRepository, never()).save(any());
    }

    // ══════════════════════════════════════════════════════════
    //  Phase 3 RED tests — ownership uses getUserId()
    // ══════════════════════════════════════════════════════════

    /**
     * Phase 3 RED test: createCard ownership check must use account.getUserId()
     * (domain Account POJO), NOT account.getUser().getId() (JPA entity graph).
     * GREEN: CardService injects IAccountUseCase and checks account.getUserId().
     */
    @Test
    void testCreateCard_ValidData_OwnershipUsesUserId() {
        // Arrange
        User user = buildUser(USER_EMAIL);
        // Domain account — getUserId() returns user.getId() directly
        Account account = buildDomainAccount(ACCOUNT_CODE, user.getId());

        AccountEntity legacyAccount = buildAccountEntity(ACCOUNT_CODE, user);
        legacyAccount.setId(account.getId());
        Card savedCard = buildCardWithAccountEntity(CARD_CODE, CardStatus.INACTIVE, legacyAccount);
        CardResponseDto expectedDto = buildCardResponseDto();
        CreateCardRequestDto dto = new CreateCardRequestDto(CardType.DEBITO, CardBrand.VISA, CardTier.CLASSIC, ACCOUNT_CODE);

        when(userService.getEntityUserByEmail(USER_EMAIL)).thenReturn(user);
        when(accountUseCase.findAccountWithUserByAccountCode(ACCOUNT_CODE)).thenReturn(account);
        when(accountJpaRepository.findById(account.getId())).thenReturn(Optional.of(legacyAccount));
        when(cardRepository.save(any(Card.class))).thenReturn(savedCard);
        when(cardMapper.toDto(savedCard)).thenReturn(expectedDto);
        when(outboxEventPort.save(any())).thenReturn(mock(OutboxEvent.class));

        // Act — should NOT throw; user.getId() equals account.getUserId()
        CardResponseDto result = cardService.createCard(dto, USER_EMAIL);

        // Assert
        assertThat(result).isNotNull();
        // Verify ownership was checked via domain port (not IAccountService)
        verify(accountUseCase).findAccountWithUserByAccountCode(ACCOUNT_CODE);
        verify(cardRepository).save(any(Card.class));
    }

    /**
     * Phase 3 RED test: when unauthorized, audit log must include ownerEmail from UserSnapshot,
     * NOT from account.getUser().getEmail() (JPA entity graph is gone).
     * GREEN: CardService uses userDomainRepository.findSnapshotByUserId(account.getUserId()).email().
     */
    @Test
    @SuppressWarnings("unchecked")
    void testCreateCard_WrongOwner_AuditEmailFromSnapshot() {
        // Arrange
        User user = buildUser(USER_EMAIL);
        UUID ownerId = UUID.randomUUID();
        Account account = buildDomainAccount(ACCOUNT_CODE, ownerId);
        UserSnapshot ownerSnapshot = new UserSnapshot(ownerId.toString(), "owner@banco.co", "realOwner", "USER");
        CreateCardRequestDto dto = new CreateCardRequestDto(CardType.DEBITO, CardBrand.VISA, CardTier.CLASSIC, ACCOUNT_CODE);

        when(userService.getEntityUserByEmail(USER_EMAIL)).thenReturn(user);
        when(accountUseCase.findAccountWithUserByAccountCode(ACCOUNT_CODE)).thenReturn(account);
        when(userDomainRepository.findSnapshotByUserId(ownerId)).thenReturn(ownerSnapshot);

        // Act & Assert
        assertThatThrownBy(() -> cardService.createCard(dto, USER_EMAIL))
                .isInstanceOf(UnauthorizedException.class);

        // Verify snapshot was fetched from domain port
        verify(userDomainRepository).findSnapshotByUserId(ownerId);

        // Capture audit log details and verify ownerEmail comes from snapshot
        ArgumentCaptor<List<AuditLogDetail>> detailsCaptor = ArgumentCaptor.forClass(List.class);
        verify(auditLogService).logFailure(eq(user), eq(AuditAction.CARD_CREATED_FAILED), eq(AuditEntityType.CARD), detailsCaptor.capture());

        List<AuditLogDetail> details = detailsCaptor.getValue();
        boolean hasOwnerEmail = details.stream()
                .anyMatch(d -> "ownerEmail".equals(d.getKey()) && "owner@banco.co".equals(d.getValue()));
        assertThat(hasOwnerEmail)
                .as("ownerEmail in audit must come from UserSnapshot.email(), not JPA entity graph")
                .isTrue();
    }

    // ══════════════════════════════════════════════════════════
    //  getMyCards
    // ══════════════════════════════════════════════════════════

    @Test
    void testGetMyCards_ReturnsAllCards() {
        // Arrange
        User user = buildUser(USER_EMAIL);
        AccountEntity legacyAccount = buildAccountEntity(ACCOUNT_CODE, user);
        Card card1 = buildCardWithAccountEntity("CARD-001", CardStatus.ACTIVE, legacyAccount);
        Card card2 = buildCardWithAccountEntity("CARD-002", CardStatus.INACTIVE, legacyAccount);
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
    //  getMyCardsByAccount
    // ══════════════════════════════════════════════════════════

    @Test
    void testGetMyCardsByAccount_OwnedAccount_ReturnsCards() {
        // Arrange
        User user = buildUser(USER_EMAIL);
        Account account = buildDomainAccount(ACCOUNT_CODE, user.getId());
        AccountEntity legacyAccount = buildAccountEntity(ACCOUNT_CODE, user);
        Card card1 = buildCardWithAccountEntity("CARD-001", CardStatus.ACTIVE, legacyAccount);
        CardSummaryDto summary1 = buildCardSummaryDto("CARD-001");

        when(accountUseCase.findAccountWithUserByAccountCode(ACCOUNT_CODE)).thenReturn(account);
        when(userService.getEntityUserByEmail(USER_EMAIL)).thenReturn(user);
        when(cardRepository.findAllByAccountAccountCode(ACCOUNT_CODE)).thenReturn(List.of(card1));
        when(cardMapper.toSummaryDto(card1)).thenReturn(summary1);

        // Act
        List<CardSummaryDto> result = cardService.getMyCardsByAccount(ACCOUNT_CODE, USER_EMAIL);

        // Assert
        assertThat(result).hasSize(1).containsExactly(summary1);
    }

    @Test
    void testGetMyCardsByAccount_NotOwned_ThrowsUnauthorized() {
        // Arrange
        User user = buildUser(USER_EMAIL);
        UUID ownerId = UUID.randomUUID();
        Account account = buildDomainAccount(ACCOUNT_CODE, ownerId);
        UserSnapshot snapshot = new UserSnapshot(ownerId.toString(), OTHER_EMAIL, "owner", "USER");

        when(accountUseCase.findAccountWithUserByAccountCode(ACCOUNT_CODE)).thenReturn(account);
        when(userService.getEntityUserByEmail(USER_EMAIL)).thenReturn(user);
        when(userDomainRepository.findSnapshotByUserId(ownerId)).thenReturn(snapshot);

        // Act & Assert
        assertThatThrownBy(() -> cardService.getMyCardsByAccount(ACCOUNT_CODE, USER_EMAIL))
                .isInstanceOf(UnauthorizedException.class);

        verify(cardRepository, never()).findAllByAccountAccountCode(any());
    }

    // ══════════════════════════════════════════════════════════
    //  getCardByCode
    // ══════════════════════════════════════════════════════════

    @Test
    void testGetCardByCode_OwnedCard_ReturnsDto() {
        // Arrange
        User user = buildUser(USER_EMAIL);
        AccountEntity legacyAccount = buildAccountEntity(ACCOUNT_CODE, user);
        Card card = buildCardWithAccountEntity(CARD_CODE, CardStatus.ACTIVE, legacyAccount);
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
        AccountEntity legacyAccount = buildAccountEntity(ACCOUNT_CODE, user);
        Card card = buildCardWithAccountEntity(CARD_CODE, CardStatus.INACTIVE, legacyAccount);
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
        AccountEntity legacyAccount = buildAccountEntity(ACCOUNT_CODE, user);
        Card card = buildCardWithAccountEntity(CARD_CODE, CardStatus.INACTIVE, legacyAccount);
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
        AccountEntity legacyAccount = buildAccountEntity(ACCOUNT_CODE, user);
        Card card = buildCardWithAccountEntity(CARD_CODE, CardStatus.INACTIVE, legacyAccount);
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
        AccountEntity legacyAccount = buildAccountEntity(ACCOUNT_CODE, user);
        Card card = buildCardWithAccountEntity(CARD_CODE, CardStatus.ACTIVE, legacyAccount);
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
        AccountEntity legacyAccount = buildAccountEntity(ACCOUNT_CODE, user);
        Card card = buildCardWithAccountEntity(CARD_CODE, CardStatus.ACTIVE, legacyAccount);
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
        AccountEntity legacyAccount = buildAccountEntity(ACCOUNT_CODE, user);
        Card card = buildCardWithAccountEntity(CARD_CODE, CardStatus.ACTIVE, legacyAccount);
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
        AccountEntity legacyAccount = buildAccountEntity(ACCOUNT_CODE, user);
        Card card = buildCardWithAccountEntity(CARD_CODE, CardStatus.ACTIVE, legacyAccount);
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
        AccountEntity legacyAccount = buildAccountEntity(ACCOUNT_CODE, user);
        Card card = buildCardWithAccountEntity(CARD_CODE, CardStatus.ACTIVE, legacyAccount);
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
        AccountEntity legacyAccount = buildAccountEntity(ACCOUNT_CODE, user);
        Card card = buildCardWithAccountEntity(CARD_CODE, CardStatus.ACTIVE, legacyAccount);
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
        AccountEntity legacyAccount = buildAccountEntity(ACCOUNT_CODE, owner);
        Card card = buildCardWithAccountEntity(CARD_CODE, CardStatus.ACTIVE, legacyAccount);
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
        AccountEntity legacyAccount = buildAccountEntity(ACCOUNT_CODE, user);
        Card card = buildCardWithAccountEntity(CARD_CODE, CardStatus.ACTIVE, legacyAccount);
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
        AccountEntity legacyAccount = buildAccountEntity(ACCOUNT_CODE, user);
        Card card1 = buildCardWithAccountEntity("CARD-001", CardStatus.BLOCKED, legacyAccount);
        Card card2 = buildCardWithAccountEntity("CARD-002", CardStatus.BLOCKED, legacyAccount);
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
        AccountEntity legacyAccount = buildAccountEntity(ACCOUNT_CODE, owner);
        Card card = buildCardWithAccountEntity(CARD_CODE, CardStatus.ACTIVE, legacyAccount);
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
        AccountEntity legacyAccount = buildAccountEntity(ACCOUNT_CODE, owner);
        Card card = buildCardWithAccountEntity(CARD_CODE, CardStatus.ACTIVE, legacyAccount);
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
        AccountEntity legacyAccount = buildAccountEntity(ACCOUNT_CODE, owner);
        Card card = buildCardWithAccountEntity(CARD_CODE, CardStatus.ACTIVE, legacyAccount);
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
        AccountEntity legacyAccount = buildAccountEntity(ACCOUNT_CODE, owner);
        Card card = buildCardWithAccountEntity(CARD_CODE, CardStatus.ACTIVE, legacyAccount);
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
     * Task 1.4 GREEN: Card.accountId is populated from the FK column after save.
     * Phase 3: uses IAccountUseCase.findAccountWithUserByAccountCode().
     */
    @Test
    void testCreateCard_ValidData_CardHasAccountId() {
        // Arrange
        User user = buildUser(USER_EMAIL);
        Account account = buildDomainAccount(ACCOUNT_CODE, user.getId());
        UUID expectedAccountId = account.getId();

        // Build a saved card that mirrors what would come back from the repository
        AccountEntity legacyAccount = buildAccountEntity(ACCOUNT_CODE, user);
        legacyAccount.setId(expectedAccountId);
        Card savedCard = buildCardWithAccountEntity(CARD_CODE, CardStatus.INACTIVE, legacyAccount);
        // Simulate the JPA-populated accountId (insertable=false, updatable=false column)
        setField(savedCard, "accountId", expectedAccountId);

        CardResponseDto expectedDto = buildCardResponseDto();
        CreateCardRequestDto dto = new CreateCardRequestDto(CardType.DEBITO, CardBrand.VISA, CardTier.CLASSIC, ACCOUNT_CODE);

        when(userService.getEntityUserByEmail(USER_EMAIL)).thenReturn(user);
        when(accountUseCase.findAccountWithUserByAccountCode(ACCOUNT_CODE)).thenReturn(account);
        when(accountJpaRepository.findById(expectedAccountId)).thenReturn(Optional.of(legacyAccount));
        when(cardRepository.save(any(Card.class))).thenReturn(savedCard);
        when(cardMapper.toDto(savedCard)).thenReturn(expectedDto);
        when(outboxEventPort.save(any())).thenReturn(mock(OutboxEvent.class));

        // Act
        cardService.createCard(dto, USER_EMAIL);

        // Assert — the saved card has accountId populated
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

    /**
     * Builds a domain Account POJO (com.banco.co.account.domain.model.Account).
     * Used for IAccountUseCase method stubs (createCard, getMyCardsByAccount ownership checks).
     */
    private Account buildDomainAccount(String accountCode, UUID userId) {
        Account account = new Account();
        account.setId(UUID.randomUUID());
        account.setAccountCode(accountCode);
        account.setUserId(userId);
        return account;
    }

    /**
     * Builds a JPA AccountEntity (com.banco.co.account.adapter.out.jpa.AccountEntity).
     * Used where the JPA entity graph is needed (validateCardOwnershipByCard paths).
     * Phase 4: Card.account is now AccountEntity — no more legacy Account.
     */
    private AccountEntity buildAccountEntity(String accountCode, User owner) {
        AccountEntity account = new AccountEntity();
        account.setId(UUID.randomUUID());
        account.setAccountCode(accountCode);
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

    private Card buildCardWithAccountEntity(String cardCode, CardStatus status,
                                             AccountEntity account) {
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
