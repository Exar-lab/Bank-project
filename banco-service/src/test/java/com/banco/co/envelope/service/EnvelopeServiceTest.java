package com.banco.co.envelope.service;

import com.banco.co.account.adapter.out.jpa.IAccountJpaRepository;
import com.banco.co.account.domain.model.Account;
import com.banco.co.account.domain.port.in.IAccountUseCase;
import com.banco.co.auditLog.service.IAuditLogService;
import com.banco.co.envelope.dto.EnvelopeRequestDto;
import com.banco.co.envelope.enums.EnvelopeStatus;
import com.banco.co.envelope.enums.EnvelopeType;
import com.banco.co.envelope.exception.EnvelopeNotFoundException;
import com.banco.co.envelope.mapper.IEnvelopeMapper;
import com.banco.co.envelope.model.Envelope;
import com.banco.co.envelope.repository.IEnvelopeRepository;
import com.banco.co.exception.authentication.UnauthorizedException;
import com.banco.co.outbox.port.IOutboxEventPort;
import com.banco.co.user.model.User;
import com.banco.co.user.service.user.IUserService;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for EnvelopeService — Mockito, no Spring context.
 * Phase 3: validates ownership checks use domain Account.getUserId(),
 * not JPA entity graph Account.getUser().getId().
 */
class EnvelopeServiceTest {

    private IEnvelopeRepository repository;
    private IAccountUseCase accountUseCase;
    private IAccountJpaRepository accountJpaRepository;
    private IUserService userService;
    private IEnvelopeMapper mapper;
    private IAuditLogService auditLogService;
    private IOutboxEventPort outboxEventPort;
    private ObjectMapper objectMapper;

    private EnvelopeService envelopeService;

    private static final String USER_EMAIL = "user@test.com";
    private static final String ACCOUNT_CODE = "ACC-2024-XYZ";

    @BeforeEach
    void setUp() {
        repository = mock(IEnvelopeRepository.class);
        accountUseCase = mock(IAccountUseCase.class);
        accountJpaRepository = mock(IAccountJpaRepository.class);
        userService = mock(IUserService.class);
        mapper = mock(IEnvelopeMapper.class);
        auditLogService = mock(IAuditLogService.class);
        outboxEventPort = mock(IOutboxEventPort.class);
        objectMapper = new ObjectMapper();

        envelopeService = new EnvelopeService(
                repository,
                accountUseCase,
                accountJpaRepository,
                userService,
                mapper,
                auditLogService,
                outboxEventPort,
                objectMapper
        );
    }

    // ══════════════════════════════════════════════════════════
    //  Phase 3 RED → GREEN: create() uses account.getUserId()
    // ══════════════════════════════════════════════════════════

    /**
     * Phase 3 RED test: create() ownership check must use account.getUserId() (domain POJO),
     * NOT account.getUser().getId() (JPA entity graph — removed after Phase 2).
     *
     * Before Phase 3: EnvelopeService injected IAccountService → no bean → fails at runtime.
     * After Phase 3 (GREEN): EnvelopeService injects IAccountUseCase → ownership via getUserId().
     */
    @Test
    void testCreate_AccountOwnedByOtherUser_ThrowsUnauthorizedException() {
        // Arrange
        User requestingUser = buildUser(USER_EMAIL);
        UUID ownerId = UUID.randomUUID(); // different from requestingUser.getId()

        // Domain Account with a userId that does NOT match the requesting user
        Account foreignAccount = buildDomainAccount(ACCOUNT_CODE, ownerId, 5);

        EnvelopeRequestDto dto = new EnvelopeRequestDto(
                "Emergency Fund",
                null,
                ACCOUNT_CODE,
                EnvelopeType.SAVINGS,
                new BigDecimal("500000"),
                null,
                false,
                null,
                null,
                false,
                null,
                null,
                null,
                5
        );

        when(userService.getEntityUserByEmail(USER_EMAIL)).thenReturn(requestingUser);
        when(accountUseCase.findAccountWithUserByAccountCode(ACCOUNT_CODE)).thenReturn(foreignAccount);

        // Act & Assert — should throw because account.getUserId() != requestingUser.getId()
        assertThatThrownBy(() -> envelopeService.create(dto, USER_EMAIL))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("You don't own this account");

        // Verify the domain port was used (not IAccountService)
        verify(accountUseCase).findAccountWithUserByAccountCode(ACCOUNT_CODE);
        // No envelope should be persisted
        verify(repository, never()).save(any(Envelope.class));
    }

    /**
     * Phase 3: getActiveAllByAccountCode() ownership check must use account.getUserId().
     */
    @Test
    void testGetActiveAllByAccountCode_NotOwned_ThrowsUnauthorizedException() {
        // Arrange
        User requestingUser = buildUser(USER_EMAIL);
        UUID ownerId = UUID.randomUUID();
        Account foreignAccount = buildDomainAccount(ACCOUNT_CODE, ownerId, 5);

        when(userService.getEntityUserByEmail(USER_EMAIL)).thenReturn(requestingUser);
        when(accountUseCase.findAccountWithUserByAccountCode(ACCOUNT_CODE)).thenReturn(foreignAccount);

        // Act & Assert
        assertThatThrownBy(() -> envelopeService.getActiveAllByAccountCode(ACCOUNT_CODE, USER_EMAIL))
                .isInstanceOf(UnauthorizedException.class);

        verify(repository, never()).findAllActiveByAccountCodeWithAccount(any());
    }

    /**
     * Phase 3: findAllByStatusAndAccountCode() ownership check must use account.getUserId().
     */
    @Test
    void testFindAllByStatusAndAccountCode_NotOwned_ThrowsUnauthorizedException() {
        // Arrange
        User requestingUser = buildUser(USER_EMAIL);
        UUID ownerId = UUID.randomUUID();
        Account foreignAccount = buildDomainAccount(ACCOUNT_CODE, ownerId, 5);

        when(userService.getEntityUserByEmail(USER_EMAIL)).thenReturn(requestingUser);
        when(accountUseCase.findAccountWithUserByAccountCode(ACCOUNT_CODE)).thenReturn(foreignAccount);

        // Act & Assert
        assertThatThrownBy(() ->
                envelopeService.findAllByStatusAndAccountCode(EnvelopeStatus.ACTIVE, ACCOUNT_CODE, USER_EMAIL))
                .isInstanceOf(UnauthorizedException.class);

        verify(repository, never()).findByAccount_AccountCodeAndStatus(any(), any());
    }

    /**
     * Phase 3: getActiveByCreatedAfter() ownership check must use account.getUserId().
     */
    @Test
    void testGetActiveByCreatedAfter_NotOwned_ThrowsUnauthorizedException() {
        // Arrange
        User requestingUser = buildUser(USER_EMAIL);
        UUID ownerId = UUID.randomUUID();
        Account foreignAccount = buildDomainAccount(ACCOUNT_CODE, ownerId, 5);

        when(userService.getEntityUserByEmail(USER_EMAIL)).thenReturn(requestingUser);
        when(accountUseCase.findAccountWithUserByAccountCode(ACCOUNT_CODE)).thenReturn(foreignAccount);

        // Act & Assert
        assertThatThrownBy(() ->
                envelopeService.getActiveByCreatedAfter(
                        java.time.LocalDateTime.now().minusDays(30), ACCOUNT_CODE, USER_EMAIL))
                .isInstanceOf(UnauthorizedException.class);

        verify(repository, never()).findActiveCreatedAfter(any(), any());
    }

    /**
     * Verifies that findActiveByEnvelopeCode throws EnvelopeNotFoundException when not found.
     * Sanity test for the internal port method.
     */
    @Test
    void testFindActiveByEnvelopeCode_NotFound_ThrowsNotFoundException() {
        // Arrange
        when(repository.findActiveByEnvelopeCode("NONEXISTENT")).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> envelopeService.findActiveByEnvelopeCode("NONEXISTENT"))
                .isInstanceOf(EnvelopeNotFoundException.class);
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
     * Ownership is tracked via userId field (not JPA entity User).
     */
    private Account buildDomainAccount(String accountCode, UUID userId, int maxEnvelope) {
        Account account = new Account();
        account.setId(UUID.randomUUID());
        account.setAccountCode(accountCode);
        account.setUserId(userId);
        account.setMaxEnvelope(maxEnvelope);
        return account;
    }
}
