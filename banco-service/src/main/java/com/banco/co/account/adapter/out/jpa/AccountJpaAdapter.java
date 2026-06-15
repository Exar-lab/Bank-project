package com.banco.co.account.adapter.out.jpa;

import com.banco.co.account.domain.model.Account;
import com.banco.co.account.domain.port.out.IAccountRepository;
import com.banco.co.account.enums.AccountType;
import com.banco.co.user.model.User;
import com.banco.co.user.repository.IUserRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * JPA adapter that implements the output port IAccountRepository.
 * Delegates to IAccountJpaRepository (Spring Data) and converts via AccountEntityMapper.
 *
 * Resolves cross-feature User reference: loads the User entity from IUserRepository
 * when persisting an Account (domain model uses userId, entity requires User FK).
 *
 * @Transactional belongs on the service (application) layer, NOT here.
 */
@Component
public class AccountJpaAdapter implements IAccountRepository {

    private final IAccountJpaRepository jpaRepository;
    private final AccountEntityMapper mapper;
    private final IUserRepository userRepository;

    public AccountJpaAdapter(
            IAccountJpaRepository jpaRepository,
            AccountEntityMapper mapper,
            IUserRepository userRepository) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
        this.userRepository = userRepository;
    }

    @Override
    public Optional<Account> findById(UUID id) {
        return jpaRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    public Optional<Account> findByAccountCode(String code) {
        return jpaRepository.findByAccountCode(code).map(mapper::toDomain);
    }

    @Override
    public Optional<Account> findByIdWithUser(UUID id) {
        return jpaRepository.findByIdWithUser(id).map(mapper::toDomain);
    }

    @Override
    public List<Account> findAllByUserId(UUID userId) {
        return jpaRepository.findAllByUserId(userId).stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public Account save(Account account) {
        AccountEntity entity = mapper.toEntity(account);
        if (account.getId() != null) {
            entity.setId(account.getId());
        }
        // Resolve User entity from userId (cross-feature FK resolution)
        if (account.getUserId() != null) {
            User user = userRepository.findById(account.getUserId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Cannot save account: user not found with id=" + account.getUserId()));
            entity.setUser(user);
        }
        return mapper.toDomain(jpaRepository.save(entity));
    }

    @Override
    public Optional<Account> findActiveById(UUID id) {
        return jpaRepository.findActiveById(id).map(mapper::toDomain);
    }

    @Override
    public Optional<Account> findActiveByAccountCode(String code) {
        return jpaRepository.findActiveByAccountCode(code).map(mapper::toDomain);
    }

    @Override
    public List<Account> findActiveAccountsByUserEmail(String email) {
        return jpaRepository.findActiveAccountsByUserEmail(email).stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public Optional<Account> findActiveByIdWithEnvelopes(UUID id) {
        return jpaRepository.findActiveByIdWithEnvelopes(id).map(mapper::toDomain);
    }

    @Override
    public boolean existsByUserEmailAndAccountType(String email, AccountType accountType) {
        return jpaRepository.existsByUserEmailAndAccountType(email, accountType);
    }

    @Override
    public Optional<Account> findByAccountCodeWithUser(String accountCode) {
        return jpaRepository.findAccountWithUser(accountCode).map(mapper::toDomain);
    }
}
