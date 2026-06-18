package com.banco.co.account.adapter.out.jpa;

import com.banco.co.account.enums.AccountType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for AccountEntity.
 * Package-private: only AccountJpaAdapter should access this directly.
 */
public interface IAccountJpaRepository extends JpaRepository<AccountEntity, UUID> {

    @Query("SELECT a FROM AccountEntity a LEFT JOIN FETCH a.user WHERE a.accountCode = :code AND a.status = 'ACTIVE'")
    @Transactional(readOnly = true)
    Optional<AccountEntity> findByAccountCode(@Param("code") String code);

    @Query("SELECT a FROM AccountEntity a LEFT JOIN FETCH a.user WHERE a.id = :id AND a.status = 'ACTIVE'")
    @Transactional(readOnly = true)
    Optional<AccountEntity> findByIdWithUser(@Param("id") UUID id);

    @Query("SELECT a FROM AccountEntity a LEFT JOIN FETCH a.user u WHERE u.id = :userId AND a.status = 'ACTIVE'")
    @Transactional(readOnly = true)
    List<AccountEntity> findAllByUserId(@Param("userId") UUID userId);

    @Query("SELECT a FROM AccountEntity a LEFT JOIN FETCH a.user WHERE a.id = :id AND a.status = 'ACTIVE'")
    @Transactional(readOnly = true)
    Optional<AccountEntity> findActiveById(@Param("id") UUID id);

    @Query("SELECT a FROM AccountEntity a LEFT JOIN FETCH a.user WHERE a.accountCode = :code AND a.status = 'ACTIVE'")
    @Transactional(readOnly = true)
    Optional<AccountEntity> findActiveByAccountCode(@Param("code") String code);

    @Query("SELECT a FROM AccountEntity a LEFT JOIN FETCH a.user u WHERE u.email = :email AND a.status = 'ACTIVE'")
    @Transactional(readOnly = true)
    List<AccountEntity> findActiveAccountsByUserEmail(@Param("email") String email);

    @Query("SELECT DISTINCT a FROM AccountEntity a LEFT JOIN FETCH a.envelopes WHERE a.id = :id AND a.status = 'ACTIVE'")
    @Transactional(readOnly = true)
    Optional<AccountEntity> findActiveByIdWithEnvelopes(@Param("id") UUID id);

    @Query("SELECT CASE WHEN COUNT(a) > 0 THEN true ELSE false END FROM AccountEntity a " +
            "LEFT JOIN a.user u WHERE u.email = :email AND a.accountType = :accountType")
    boolean existsByUserEmailAndAccountType(@Param("email") String email, @Param("accountType") AccountType accountType);

    /**
     * Find account by code with user context eagerly loaded.
     * Used by AccountJpaAdapter.findByAccountCodeWithUser — equivalent to legacy findAccountWithUser.
     */
    @Query("SELECT a FROM AccountEntity a LEFT JOIN FETCH a.user WHERE a.accountCode = :accountCode")
    @Transactional(readOnly = true)
    Optional<AccountEntity> findAccountWithUser(@Param("accountCode") String accountCode);
}
