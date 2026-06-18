package com.banco.co.account.service;

import com.banco.co.account.domain.model.Account;
import com.banco.co.account.domain.port.in.IAccountUseCase;
import com.banco.co.account.domain.port.out.IAccountRepository;
import com.banco.co.account.dto.AccountRequestDto;
import com.banco.co.account.dto.AccountResponseDto;
import com.banco.co.account.dto.AccountUpdateDto;
import com.banco.co.account.enums.AccountStatus;
import com.banco.co.account.enums.AccountType;
import com.banco.co.account.exception.account.*;
import com.banco.co.auditLog.service.IAuditLogService;
import com.banco.co.exception.authentication.UnauthorizedException;
import com.banco.co.outbox.port.IOutboxEventPort;
import com.banco.co.user.domain.model.UserSnapshot;
import com.banco.co.user.domain.port.out.IUserRepository;
import com.banco.co.user.model.User;
import com.banco.co.user.service.user.IUserService;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Task 2.1 — AccountService unit tests.
 * All tests mock com.banco.co.account.domain.port.out.IAccountRepository (domain port).
 * The legacy IAccountRepository (Spring Data) MUST NOT be referenced here.
 *
 * RED: tests fail because AccountService still injects the legacy repo.
 * GREEN: after AccountService rewrite onto domain port, all 10 scenarios pass.
 */
@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    // ── Domain output port — the only persistence mock ────────────────────────
    @Mock
    private IAccountRepository domainAccountRepository;

    @Mock
    private IUserRepository userDomainRepository;

    @Mock
    private IUserService userService;

    @Mock
    private IAuditLogService auditLogService;

    @Mock
    private IOutboxEventPort outboxEventPort;

    private IAccountUseCase accountService;

    // ── Test data ─────────────────────────────────────────────────────────────

    private static final UUID USER_ID     = UUID.randomUUID();
    private static final UUID ACCOUNT_ID  = UUID.randomUUID();
    private static final UUID OTHER_USER  = UUID.randomUUID();
    private static final String USER_EMAIL = "owner@test.com";
    private static final String ACCOUNT_CODE = "ACC-001";

    @BeforeEach
    void setUp() {
        accountService = new AccountService(
                domainAccountRepository,
                userDomainRepository,
                userService,
                auditLogService,
                outboxEventPort,
                new ObjectMapper()
        );
    }

    // ── Helper builders ────────────────────────────────────────────────────────

    private Account buildDomainAccount(UUID userId, AccountStatus status, BigDecimal balance) {
        Account a = new Account();
        a.setId(ACCOUNT_ID);
        a.setAccountCode(ACCOUNT_CODE);
        a.setAccountNumber("1234567890");
        a.setUserId(userId);
        a.setStatus(status);
        a.setBalance(balance);
        a.setAccountType(AccountType.SAVINGS);
        a.setCurrency("CRC");
        a.setOverdraftLimit(BigDecimal.ZERO);
        return a;
    }

    private User buildUser(UUID id, String email) {
        User u = new User();
        u.setId(id);
        u.setEmail(email);
        return u;
    }

    private UserSnapshot buildSnapshot(UUID id, String email) {
        return new UserSnapshot(id.toString(), email, "owner", "ROLE_USER");
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Scenario 1 — createAccount: valid data saves via domain port
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * RED: AccountService still calls legacy IAccountRepository — the domain port save() is never called.
     * GREEN: after rewrite, domainAccountRepository.save() is invoked exactly once.
     */
    @Test
    void testCreateAccount_ValidData_SavesAndReturnsDto() {
        // Arrange
        AccountRequestDto dto = new AccountRequestDto(
                AccountType.SAVINGS, "CRC", BigDecimal.ZERO, BigDecimal.ZERO, "123456789");

        UserSnapshot snapshot = buildSnapshot(USER_ID, USER_EMAIL);
        User user = buildUser(USER_ID, USER_EMAIL);

        when(userDomainRepository.findSnapshotByEmail(USER_EMAIL)).thenReturn(snapshot);
        when(userService.getEntityUserByEmail(USER_EMAIL)).thenReturn(user);
        when(domainAccountRepository.existsByUserEmailAndAccountType(USER_EMAIL, AccountType.SAVINGS))
                .thenReturn(false);

        Account savedAccount = buildDomainAccount(USER_ID, AccountStatus.ACTIVE, BigDecimal.ZERO);
        when(domainAccountRepository.save(any(Account.class))).thenReturn(savedAccount);
        when(userDomainRepository.findSnapshotByUserId(USER_ID)).thenReturn(snapshot);
        when(outboxEventPort.save(any())).thenReturn(null);

        // Act
        AccountResponseDto result = accountService.createAccount(dto, USER_EMAIL);

        // Assert — domain port save called exactly once; legacy repo NOT involved
        verify(domainAccountRepository, times(1)).save(any(Account.class));
        assertThat(result).isNotNull();
        assertThat(result.accountType()).isEqualTo(AccountType.SAVINGS);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Scenario 2 — createAccount: duplicate account type throws exception
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void testCreateAccount_DuplicateAccountType_ThrowsAccountDuplicatedTypeException() {
        AccountRequestDto dto = new AccountRequestDto(
                AccountType.SAVINGS, "CRC", BigDecimal.ZERO, BigDecimal.ZERO, "123456789");

        UserSnapshot snapshot = buildSnapshot(USER_ID, USER_EMAIL);
        User user = buildUser(USER_ID, USER_EMAIL);

        when(userDomainRepository.findSnapshotByEmail(USER_EMAIL)).thenReturn(snapshot);
        when(userService.getEntityUserByEmail(USER_EMAIL)).thenReturn(user);
        when(domainAccountRepository.existsByUserEmailAndAccountType(USER_EMAIL, AccountType.SAVINGS))
                .thenReturn(true);

        assertThatThrownBy(() -> accountService.createAccount(dto, USER_EMAIL))
                .isInstanceOf(AccountDuplicatedTypeException.class);

        verify(domainAccountRepository, never()).save(any());
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Scenario 3 — getAccount: valid owner returns DTO
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void testGetAccount_ValidOwner_ReturnsDto() {
        Account account = buildDomainAccount(USER_ID, AccountStatus.ACTIVE, BigDecimal.TEN);
        User user = buildUser(USER_ID, USER_EMAIL);
        UserSnapshot snapshot = buildSnapshot(USER_ID, USER_EMAIL);

        when(domainAccountRepository.findActiveById(ACCOUNT_ID)).thenReturn(Optional.of(account));
        when(userService.getEntityUserByEmail(USER_EMAIL)).thenReturn(user);
        when(userDomainRepository.findSnapshotByUserId(USER_ID)).thenReturn(snapshot);

        AccountResponseDto result = accountService.getAccount(ACCOUNT_ID, USER_EMAIL);

        assertThat(result).isNotNull();
        assertThat(result.accountCode()).isEqualTo(ACCOUNT_CODE);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Scenario 4 — getAccount: wrong owner throws UnauthorizedException
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void testGetAccount_WrongOwner_ThrowsUnauthorizedException() {
        Account account = buildDomainAccount(OTHER_USER, AccountStatus.ACTIVE, BigDecimal.TEN);
        User user = buildUser(USER_ID, USER_EMAIL); // different userId from account owner

        when(domainAccountRepository.findActiveById(ACCOUNT_ID)).thenReturn(Optional.of(account));
        when(userService.getEntityUserByEmail(USER_EMAIL)).thenReturn(user);

        assertThatThrownBy(() -> accountService.getAccount(ACCOUNT_ID, USER_EMAIL))
                .isInstanceOf(UnauthorizedException.class);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Scenario 5 — closeAccount: non-zero balance throws AccountNotEmptyException
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void testCloseAccount_NonZeroBalance_ThrowsAccountNotEmptyException() {
        Account account = buildDomainAccount(USER_ID, AccountStatus.ACTIVE, new BigDecimal("100.00"));
        User user = buildUser(USER_ID, USER_EMAIL);

        when(domainAccountRepository.findActiveByIdWithEnvelopes(ACCOUNT_ID)).thenReturn(Optional.of(account));
        when(userService.getEntityUserByEmail(USER_EMAIL)).thenReturn(user);

        assertThatThrownBy(() -> accountService.closeAccount(ACCOUNT_ID, USER_EMAIL))
                .isInstanceOf(AccountNotEmptyException.class);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Scenario 6 — updateBalance: calls domain repository save
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void testUpdateBalance_ValidAccount_CallsDomainRepository() {
        Account account = buildDomainAccount(USER_ID, AccountStatus.ACTIVE, BigDecimal.TEN);

        accountService.updateBalance(account);

        verify(domainAccountRepository, times(1)).save(account);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Scenario 7 — getAccountById: not found throws AccountNotFoundException
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void testGetAccountById_NotFound_ThrowsAccountNotFoundException() {
        UUID unknownId = UUID.randomUUID();
        when(domainAccountRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.getAccountById(unknownId))
                .isInstanceOf(AccountNotFoundException.class);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Scenario 8 — findAccountWithUserByAccountCode: not found throws exception
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void testFindAccountWithUserByAccountCode_NotFound_ThrowsAccountNotFoundException() {
        when(domainAccountRepository.findByAccountCodeWithUser("NONEXISTENT")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.findAccountWithUserByAccountCode("NONEXISTENT"))
                .isInstanceOf(AccountNotFoundException.class);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Scenario 9 — validateCanReceiveDeposit: inactive account throws exception
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void testValidateCanReceiveDeposit_InactiveAccount_ThrowsAccountNotActiveException() {
        Account account = buildDomainAccount(USER_ID, AccountStatus.CLOSED, BigDecimal.ZERO);

        assertThatThrownBy(() -> accountService.validateCanReceiveDeposit(account))
                .isInstanceOf(AccountNotActiveException.class);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Scenario 10 — validateCanWithdraw: insufficient funds throws exception
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void testValidateCanWithdraw_InsufficientFunds_ThrowsAccountInsufficientFundsException() {
        Account account = buildDomainAccount(USER_ID, AccountStatus.ACTIVE, new BigDecimal("10.00"));

        assertThatThrownBy(() -> accountService.validateCanWithdraw(account, new BigDecimal("100.00")))
                .isInstanceOf(AccountInsufficientFundsException.class);
    }
}
