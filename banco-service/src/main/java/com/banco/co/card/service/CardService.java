package com.banco.co.card.service;

import com.banco.co.account.model.Account;
import com.banco.co.account.service.IAccountService;
import com.banco.co.auditLog.enums.AuditAction;
import com.banco.co.auditLog.enums.AuditEntityType;
import com.banco.co.auditLog.model.AuditLogDetail;
import com.banco.co.auditLog.service.IAuditLogService;
import com.banco.co.card.dto.*;
import com.banco.co.card.enums.CardStatus;
import com.banco.co.card.exception.card.CardNotFoundException;
import com.banco.co.card.mapper.ICardMapper;
import com.banco.co.card.model.Card;
import com.banco.co.card.repository.ICardRepository;
import com.banco.co.exception.authentication.UnauthorizedException;
import com.banco.co.outbox.enums.KafkaTopic;
import com.banco.co.outbox.model.OutboxEvent;
import com.banco.co.outbox.port.IOutboxEventPort;
import com.banco.co.security.securityhasher.HashUtils;
import com.banco.co.user.model.User;
import com.banco.co.user.service.user.IUserService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class CardService implements ICardService {

    private final ICardRepository cardRepository;
    private final IAccountService accountService;
    private final IUserService userService;
    private final IAuditLogService auditLogService;
    private final ICardMapper cardMapper;
    private final IOutboxEventPort outboxEventPort;
    private final ObjectMapper objectMapper;

    // ══════════════════════════════════════════════════════════
    //  INTERNAL
    // ══════════════════════════════════════════════════════════

    @Override
    @Transactional(readOnly = true)
    public Card findCardWithAccountByCardCode(String cardCode) {
        return cardRepository.findByCardCodeWithAccount(cardCode)
                .orElseThrow(() -> new CardNotFoundException(cardCode));
    }

    // ══════════════════════════════════════════════════════════
    //  createCard
    // ══════════════════════════════════════════════════════════

    @Override
    @Transactional
    public CardResponseDto createCard(CreateCardRequestDto dto, String userEmail) {

        User user = userService.getEntityUserByEmail(userEmail);
        Account account = accountService.findAccountWithUserByAccountCode(dto.accountCode());

        if (!account.getUser().getId().equals(user.getId())) {
            auditLogService.logFailure(
                    user,
                    AuditAction.CARD_CREATED_FAILED,
                    AuditEntityType.CARD,
                    List.of(
                            new AuditLogDetail("message", "Security Violation: User attempted to create Card on Account belonging to other User"),
                            new AuditLogDetail("userEmail", userEmail),
                            new AuditLogDetail("accountCode", dto.accountCode()),
                            new AuditLogDetail("ownerId", account.getUser().getId()),
                            new AuditLogDetail("ownerEmail", account.getUser().getEmail())
                    )
            );
            log.warn("Unauthorized card creation attempt: user {} on account {}", userEmail, dto.accountCode());
            throw new UnauthorizedException("You don't own this account");
        }

        Card card = new Card();
        card.setCardType(dto.cardType());
        card.setBrand(dto.brand());
        card.setTier(dto.tier());
        card.setAccount(account);
        card.setPinHash(HashUtils.hashBcrypt("000000"));

        Card savedCard = cardRepository.save(card);

        auditLogService.logSuccess(
                user,
                AuditAction.CARD_CREATED,
                AuditEntityType.CARD,
                savedCard.getId().toString(),
                List.of(
                        new AuditLogDetail("message", "Card created successfully"),
                        new AuditLogDetail("cardCode", savedCard.getCardCode()),
                        new AuditLogDetail("accountCode", dto.accountCode()),
                        new AuditLogDetail("cardType", dto.cardType()),
                        new AuditLogDetail("brand", dto.brand()),
                        new AuditLogDetail("tier", dto.tier())
                )
        );

        outboxEventPort.save(new OutboxEvent(
                "Card",
                savedCard.getId().toString(),
                "CardCreated",
                buildCardPayload(savedCard, "CardCreated"),
                KafkaTopic.CARD_EVENTS
        ));

        log.info("Card {} created for account {} by user {}", savedCard.getCardCode(), dto.accountCode(), userEmail);

        return cardMapper.toDto(savedCard);
    }

    // ══════════════════════════════════════════════════════════
    //  getMyCards
    // ══════════════════════════════════════════════════════════

    @Override
    @Transactional(readOnly = true)
    public List<CardSummaryDto> getMyCards(String userEmail) {
        log.info("Retrieving all cards for user {}", userEmail);
        return cardRepository.findAllByAccountUserEmail(userEmail).stream()
                .map(cardMapper::toSummaryDto)
                .toList();
    }

    // ══════════════════════════════════════════════════════════
    //  getMyCardsByAccount
    // ══════════════════════════════════════════════════════════

    @Override
    @Transactional(readOnly = true)
    public List<CardSummaryDto> getMyCardsByAccount(String accountCode, String userEmail) {
        Account account = accountService.findAccountWithUserByAccountCode(accountCode);
        User user = userService.getEntityUserByEmail(userEmail);

        validateCardOwnership(account, user, accountCode);

        log.info("Retrieving cards for account {} by user {}", accountCode, userEmail);
        return cardRepository.findAllByAccountAccountCode(accountCode).stream()
                .map(cardMapper::toSummaryDto)
                .toList();
    }

    // ══════════════════════════════════════════════════════════
    //  getCardByCode
    // ══════════════════════════════════════════════════════════

    @Override
    @Transactional(readOnly = true)
    public CardResponseDto getCardByCode(String cardCode, String userEmail) {
        Card card = cardRepository.findByCardCodeWithAccount(cardCode)
                .orElseThrow(() -> new CardNotFoundException(cardCode));

        User user = userService.getEntityUserByEmail(userEmail);
        validateCardOwnershipByCard(card, user);

        log.info("Card {} retrieved by user {}", cardCode, userEmail);
        return cardMapper.toDto(card);
    }

    // ══════════════════════════════════════════════════════════
    //  activateCard
    // ══════════════════════════════════════════════════════════

    @Override
    @Transactional
    public CardResponseDto activateCard(String cardCode, ActivateCardRequestDto dto, String userEmail) {
        Card card = cardRepository.findByCardCodeWithAccount(cardCode)
                .orElseThrow(() -> new CardNotFoundException(cardCode));

        User user = userService.getEntityUserByEmail(userEmail);
        validateCardOwnershipByCard(card, user);

        card.activate(dto.pin());
        Card savedCard = cardRepository.save(card);

        auditLogService.logSuccess(
                user,
                AuditAction.CARD_ACTIVATED,
                AuditEntityType.CARD,
                savedCard.getId().toString(),
                List.of(
                        new AuditLogDetail("message", "Card activated successfully"),
                        new AuditLogDetail("cardCode", cardCode),
                        new AuditLogDetail("status", savedCard.getStatus())
                        // PIN is intentionally excluded from audit
                )
        );

        outboxEventPort.save(new OutboxEvent(
                "Card",
                savedCard.getId().toString(),
                "CardActivated",
                buildCardPayload(savedCard, "CardActivated"),
                KafkaTopic.CARD_EVENTS
        ));

        log.info("Card {} activated by user {}", cardCode, userEmail);
        return cardMapper.toDto(savedCard);
    }

    // ══════════════════════════════════════════════════════════
    //  blockCard
    // ══════════════════════════════════════════════════════════

    @Override
    @Transactional
    public CardResponseDto blockCard(String cardCode, BlockCardRequestDto dto, String userEmail) {
        Card card = cardRepository.findByCardCodeWithAccount(cardCode)
                .orElseThrow(() -> new CardNotFoundException(cardCode));

        User user = userService.getEntityUserByEmail(userEmail);
        validateCardOwnershipByCard(card, user);

        card.block(dto.reason());
        Card savedCard = cardRepository.save(card);

        auditLogService.logSuccess(
                user,
                AuditAction.CARD_BLOCKED,
                AuditEntityType.CARD,
                savedCard.getId().toString(),
                List.of(
                        new AuditLogDetail("message", "Card blocked"),
                        new AuditLogDetail("cardCode", cardCode),
                        new AuditLogDetail("status", savedCard.getStatus()),
                        new AuditLogDetail("reason", dto.reason())
                )
        );

        outboxEventPort.save(new OutboxEvent(
                "Card",
                savedCard.getId().toString(),
                "CardBlocked",
                buildCardPayload(savedCard, "CardBlocked"),
                KafkaTopic.CARD_EVENTS
        ));

        log.info("Card {} blocked by user {} — reason: {}", cardCode, userEmail, dto.reason());
        return cardMapper.toDto(savedCard);
    }

    // ══════════════════════════════════════════════════════════
    //  reportStolen
    // ══════════════════════════════════════════════════════════

    @Override
    @Transactional
    public CardResponseDto reportStolen(String cardCode, String userEmail) {
        Card card = cardRepository.findByCardCodeWithAccount(cardCode)
                .orElseThrow(() -> new CardNotFoundException(cardCode));

        User user = userService.getEntityUserByEmail(userEmail);
        validateCardOwnershipByCard(card, user);

        card.reportStolen();
        Card savedCard = cardRepository.save(card);

        auditLogService.logSuccess(
                user,
                AuditAction.CARD_STOLEN_REPORTED,
                AuditEntityType.CARD,
                savedCard.getId().toString(),
                List.of(
                        new AuditLogDetail("message", "Card reported as stolen"),
                        new AuditLogDetail("cardCode", cardCode),
                        new AuditLogDetail("status", savedCard.getStatus())
                )
        );

        outboxEventPort.save(new OutboxEvent(
                "Card",
                savedCard.getId().toString(),
                "CardStolenReported",
                buildCardPayload(savedCard, "CardStolenReported"),
                KafkaTopic.CARD_EVENTS
        ));

        log.warn("Card {} reported as stolen by user {}", cardCode, userEmail);
        return cardMapper.toDto(savedCard);
    }

    // ══════════════════════════════════════════════════════════
    //  reportLost
    // ══════════════════════════════════════════════════════════

    @Override
    @Transactional
    public CardResponseDto reportLost(String cardCode, String userEmail) {
        Card card = cardRepository.findByCardCodeWithAccount(cardCode)
                .orElseThrow(() -> new CardNotFoundException(cardCode));

        User user = userService.getEntityUserByEmail(userEmail);
        validateCardOwnershipByCard(card, user);

        card.reportLost(); // throws CardClosedException if already CLOSED or STOLEN
        Card savedCard = cardRepository.save(card);

        auditLogService.logSuccess(
                user,
                AuditAction.CARD_LOST_REPORTED,
                AuditEntityType.CARD,
                savedCard.getId().toString(),
                List.of(
                        new AuditLogDetail("message", "Card reported as lost"),
                        new AuditLogDetail("cardCode", cardCode),
                        new AuditLogDetail("status", savedCard.getStatus())
                )
        );

        outboxEventPort.save(new OutboxEvent(
                "Card",
                savedCard.getId().toString(),
                "CardLostReported",
                buildCardPayload(savedCard, "CardLostReported"),
                KafkaTopic.CARD_EVENTS
        ));

        log.warn("Card {} reported as lost by user {}", cardCode, userEmail);
        return cardMapper.toDto(savedCard);
    }

    // ══════════════════════════════════════════════════════════
    //  closeCard
    // ══════════════════════════════════════════════════════════

    @Override
    @Transactional
    public CardResponseDto closeCard(String cardCode, String userEmail) {
        Card card = cardRepository.findByCardCodeWithAccount(cardCode)
                .orElseThrow(() -> new CardNotFoundException(cardCode));

        User user = userService.getEntityUserByEmail(userEmail);
        validateCardOwnershipByCard(card, user);

        card.close(); // throws CardClosedException if already CLOSED
        Card savedCard = cardRepository.save(card);

        auditLogService.logSuccess(
                user,
                AuditAction.CARD_CLOSED,
                AuditEntityType.CARD,
                savedCard.getId().toString(),
                List.of(
                        new AuditLogDetail("message", "Card closed by cardholder"),
                        new AuditLogDetail("cardCode", cardCode),
                        new AuditLogDetail("status", savedCard.getStatus())
                )
        );

        outboxEventPort.save(new OutboxEvent(
                "Card",
                savedCard.getId().toString(),
                "CardClosed",
                buildCardPayload(savedCard, "CardClosed"),
                KafkaTopic.CARD_EVENTS
        ));

        log.info("Card {} closed by user {}", cardCode, userEmail);
        return cardMapper.toDto(savedCard);
    }

    // ══════════════════════════════════════════════════════════
    //  ADMIN USE CASES — stubs (not part of TASK-10)
    // ══════════════════════════════════════════════════════════

    // ══════════════════════════════════════════════════════════
    //  updateLimits
    // ══════════════════════════════════════════════════════════

    @Override
    @Transactional
    public CardResponseDto updateLimits(String cardCode, UpdateCardLimitsRequestDto dto, String userEmail) {
        Card card = cardRepository.findByCardCodeWithAccount(cardCode)
                .orElseThrow(() -> new CardNotFoundException(cardCode));

        User user = userService.getEntityUserByEmail(userEmail);
        validateCardOwnershipByCard(card, user);

        if (dto.dailyLimit().compareTo(dto.monthlyLimit()) > 0) {
            throw new IllegalArgumentException("Daily limit cannot exceed monthly limit");
        }

        BigDecimal oldDailyLimit = card.getDailyLimit();
        BigDecimal oldMonthlyLimit = card.getMonthlyLimit();

        card.setDailyLimit(dto.dailyLimit());
        card.setMonthlyLimit(dto.monthlyLimit());
        Card savedCard = cardRepository.save(card);

        auditLogService.logSuccess(
                user,
                AuditAction.CARD_LIMITS_UPDATED,
                AuditEntityType.CARD,
                savedCard.getId().toString(),
                List.of(
                        new AuditLogDetail("cardCode", cardCode),
                        new AuditLogDetail("oldDailyLimit", oldDailyLimit),
                        new AuditLogDetail("newDailyLimit", dto.dailyLimit()),
                        new AuditLogDetail("oldMonthlyLimit", oldMonthlyLimit),
                        new AuditLogDetail("newMonthlyLimit", dto.monthlyLimit())
                )
        );

        outboxEventPort.save(buildLimitsOutboxEvent(savedCard));

        log.info("Card {} limits updated by user {} — daily: {} → {}, monthly: {} → {}",
                cardCode, userEmail, oldDailyLimit, dto.dailyLimit(), oldMonthlyLimit, dto.monthlyLimit());

        return cardMapper.toDto(savedCard);
    }

    // ══════════════════════════════════════════════════════════
    //  updateFeatures
    // ══════════════════════════════════════════════════════════

    @Override
    @Transactional
    public CardResponseDto updateFeatures(String cardCode, UpdateCardFeaturesRequestDto dto, String userEmail) {
        Card card = cardRepository.findByCardCodeWithAccount(cardCode)
                .orElseThrow(() -> new CardNotFoundException(cardCode));

        User user = userService.getEntityUserByEmail(userEmail);
        validateCardOwnershipByCard(card, user);

        if (dto.contactlessEnabled() != null) card.setContactlessEnabled(dto.contactlessEnabled());
        if (dto.onlinePaymentsEnabled() != null) card.setOnlinePaymentsEnabled(dto.onlinePaymentsEnabled());
        if (dto.internationalEnabled() != null) card.setInternationalEnabled(dto.internationalEnabled());

        Card savedCard = cardRepository.save(card);

        auditLogService.logSuccess(
                user,
                AuditAction.CARD_FEATURES_UPDATED,
                AuditEntityType.CARD,
                savedCard.getId().toString(),
                List.of(
                        new AuditLogDetail("cardCode", cardCode),
                        new AuditLogDetail("contactlessEnabled", savedCard.isContactlessEnabled()),
                        new AuditLogDetail("onlinePaymentsEnabled", savedCard.isOnlinePaymentsEnabled()),
                        new AuditLogDetail("internationalEnabled", savedCard.isInternationalEnabled())
                )
        );

        outboxEventPort.save(buildFeaturesOutboxEvent(savedCard));

        log.info("Card {} features updated by user {}", cardCode, userEmail);

        return cardMapper.toDto(savedCard);
    }

    // ══════════════════════════════════════════════════════════
    //  adminGetAllByStatus
    // ══════════════════════════════════════════════════════════

    @Override
    @Transactional(readOnly = true)
    public Page<CardSummaryDto> adminGetAllByStatus(CardStatus status, Pageable pageable) {
        return cardRepository.findAllByStatus(status, pageable)
                .map(cardMapper::toSummaryDto);
    }

    // ══════════════════════════════════════════════════════════
    //  adminChangeStatus
    // ══════════════════════════════════════════════════════════

    @Override
    @Transactional
    public CardResponseDto adminChangeStatus(String cardCode, AdminChangeCardStatusRequestDto dto, String adminEmail) {
        Card card = cardRepository.findByCardCodeWithAccount(cardCode)
                .orElseThrow(() -> new CardNotFoundException(cardCode));

        CardStatus oldStatus = card.getStatus();
        card.setStatus(dto.status());
        if (dto.reason() != null) {
            card.setBlockedReason(dto.reason());
        }
        Card savedCard = cardRepository.save(card);

        auditLogService.logAnonymous(
                AuditAction.CARD_STATUS_CHANGED,
                AuditEntityType.CARD,
                savedCard.getId().toString(),
                List.of(
                        new AuditLogDetail("cardCode", cardCode),
                        new AuditLogDetail("oldStatus", oldStatus),
                        new AuditLogDetail("newStatus", dto.status()),
                        new AuditLogDetail("adminEmail", adminEmail),
                        new AuditLogDetail("reason", dto.reason())
                )
        );

        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("cardCode", cardCode);
            payload.put("accountCode", savedCard.getAccount() != null ? savedCard.getAccount().getAccountCode() : null);
            payload.put("oldStatus", oldStatus.toString());
            payload.put("newStatus", dto.status().toString());
            payload.put("adminEmail", adminEmail);
            outboxEventPort.save(new OutboxEvent(
                    "Card",
                    savedCard.getId().toString(),
                    "CardStatusChanged",
                    objectMapper.writeValueAsString(payload),
                    KafkaTopic.CARD_EVENTS
            ));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize CardStatusChanged event payload", e);
        }

        log.warn("Card {} status changed from {} to {} by admin {}", cardCode, oldStatus, dto.status(), adminEmail);
        return cardMapper.toDto(savedCard);
    }

    // ══════════════════════════════════════════════════════════
    //  adminResetPin
    // ══════════════════════════════════════════════════════════

    @Override
    @Transactional
    public void adminResetPin(String cardCode, AdminResetPinRequestDto dto, String adminEmail) {
        Card card = cardRepository.findByCardCode(cardCode)
                .orElseThrow(() -> new CardNotFoundException(cardCode));

        card.setPinHash(HashUtils.hashBcrypt(dto.newPin()));
        cardRepository.save(card);

        auditLogService.logAnonymous(
                AuditAction.CARD_PIN_RESET,
                AuditEntityType.CARD,
                card.getId().toString(),
                List.of(
                        new AuditLogDetail("cardCode", cardCode),
                        new AuditLogDetail("adminEmail", adminEmail)
                        // newPin intentionally excluded from audit
                )
        );

        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("cardCode", cardCode);
            payload.put("adminEmail", adminEmail);
            // newPin intentionally excluded from outbox payload
            outboxEventPort.save(new OutboxEvent(
                    "Card",
                    card.getId().toString(),
                    "CardPinReset",
                    objectMapper.writeValueAsString(payload),
                    KafkaTopic.CARD_EVENTS
            ));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize CardPinReset event payload", e);
        }

        log.warn("Card {} PIN reset by admin {}", cardCode, adminEmail);
    }

    // ══════════════════════════════════════════════════════════
    //  PRIVATE HELPERS
    // ══════════════════════════════════════════════════════════

    /**
     * Validates that the authenticated user owns the account (used before a card is created).
     */
    private void validateCardOwnership(Account account, User user, String accountCode) {
        if (!account.getUser().getId().equals(user.getId())) {
            auditLogService.logFailure(
                    user,
                    AuditAction.CARD_READ,
                    AuditEntityType.CARD,
                    List.of(
                            new AuditLogDetail("message", "Security Violation: User attempted to access Cards on Account belonging to other User"),
                            new AuditLogDetail("userId", user.getId()),
                            new AuditLogDetail("userEmail", user.getEmail()),
                            new AuditLogDetail("accountCode", accountCode),
                            new AuditLogDetail("ownerId", account.getUser().getId()),
                            new AuditLogDetail("ownerEmail", account.getUser().getEmail())
                    )
            );
            throw new UnauthorizedException("You don't own this account");
        }
    }

    /**
     * Validates that the authenticated user owns the card (by comparing emails).
     */
    private void validateCardOwnershipByCard(Card card, User user) {
        String cardOwnerEmail = card.getAccount().getUser().getEmail();
        if (!cardOwnerEmail.equals(user.getEmail())) {
            auditLogService.logFailure(
                    user,
                    AuditAction.CARD_READ,
                    AuditEntityType.CARD,
                    List.of(
                            new AuditLogDetail("message", "Security Violation: User attempted to access Card belonging to other User"),
                            new AuditLogDetail("userId", user.getId()),
                            new AuditLogDetail("userEmail", user.getEmail()),
                            new AuditLogDetail("cardCode", card.getCardCode()),
                            new AuditLogDetail("ownerEmail", cardOwnerEmail)
                    )
            );
            throw new UnauthorizedException("You don't own this card");
        }
    }

    /**
     * Builds an outbox event for a CardLimitsUpdated event.
     */
    private OutboxEvent buildLimitsOutboxEvent(Card card) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("eventType", "CardLimitsUpdated");
            payload.put("cardCode", card.getCardCode());
            payload.put("accountCode", card.getAccount() != null ? card.getAccount().getAccountCode() : null);
            payload.put("dailyLimit", card.getDailyLimit());
            payload.put("monthlyLimit", card.getMonthlyLimit());
            return new OutboxEvent(
                    "Card",
                    card.getId().toString(),
                    "CardLimitsUpdated",
                    objectMapper.writeValueAsString(payload),
                    KafkaTopic.CARD_EVENTS
            );
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize CardLimitsUpdated event payload", e);
        }
    }

    /**
     * Builds an outbox event for a CardFeaturesUpdated event.
     */
    private OutboxEvent buildFeaturesOutboxEvent(Card card) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("eventType", "CardFeaturesUpdated");
            payload.put("cardCode", card.getCardCode());
            payload.put("accountCode", card.getAccount() != null ? card.getAccount().getAccountCode() : null);
            payload.put("contactlessEnabled", card.isContactlessEnabled());
            payload.put("onlinePaymentsEnabled", card.isOnlinePaymentsEnabled());
            payload.put("internationalEnabled", card.isInternationalEnabled());
            return new OutboxEvent(
                    "Card",
                    card.getId().toString(),
                    "CardFeaturesUpdated",
                    objectMapper.writeValueAsString(payload),
                    KafkaTopic.CARD_EVENTS
            );
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize CardFeaturesUpdated event payload", e);
        }
    }

    /**
     * Builds a safe outbox payload — NEVER includes cardNumber, pinHash, or securityCode.
     */
    private String buildCardPayload(Card card, String eventType) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("eventType", eventType);
            payload.put("cardId", card.getId().toString());
            payload.put("cardCode", card.getCardCode());
            payload.put("accountCode", card.getAccount() != null ? card.getAccount().getAccountCode() : null);
            payload.put("cardType", card.getCardType() != null ? card.getCardType().toString() : null);
            payload.put("status", card.getStatus().toString());
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize card event payload", e);
        }
    }
}
