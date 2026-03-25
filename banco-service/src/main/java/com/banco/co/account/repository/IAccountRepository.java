package com.banco.co.account.repository;

import com.banco.co.account.enums.AccountType;
import com.banco.co.account.model.Account;
import com.banco.co.user.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
@Repository
public interface IAccountRepository extends JpaRepository<Account, UUID> {

    @Query("SELECT a FROM Account a LEFT JOIN FETCH a.user u WHERE u.email = :email AND a.status = 'ACTIVE'")
    @Transactional(readOnly = true)
    Optional<Account> findFirstActiveAccountByUser_Email(@Param("email") String email);

    @Query("SELECT a FROM Account a LEFT JOIN FETCH a.user u WHERE u.documentNumber = :documentNumber AND a.status = 'ACTIVE'")
    @Transactional(readOnly = true)
    Optional<Account> findFirstActiveAccountByUser_DocumentNumber(@Param("documentNumber") String documentNumber);

    @Query("SELECT a FROM Account a LEFT JOIN FETCH a.user u WHERE a.accountCode = :code AND a.status = 'ACTIVE'")
    @Transactional(readOnly = true)
    Optional<Account> findActiveAccountByAccountCode(@Param("code") String accountCode);

    @Query("SELECT a FROM Account a LEFT JOIN FETCH a.user u WHERE u.email = :email AND a.status = 'ACTIVE'")
    @Transactional(readOnly = true)
    List<Account> findActiveAccountsByUser_Email(@Param("email") String email);

    @Transactional(readOnly = true)
    boolean existsByUser_EmailAndAccountType(String userEmail, AccountType accountType);

    @Query("SELECT a FROM Account a LEFT JOIN FETCH a.user u WHERE a.createdAt > :date")
    @Transactional(readOnly = true)
    List<Account> findAllByCreatedAtAfter(@Param("date") LocalDateTime date);
    @Query("SELECT a FROM Account a " +
            "LEFT JOIN FETCH a.user u " +
            "WHERE a.accountCode = :accountCode")
    @Transactional(readOnly = true)
    Optional<Account> findAccountWithUser(@Param("accountCode") String accountCode);

    @Query("SELECT a FROM Account a LEFT JOIN FETCH a.user u WHERE a.id = :id AND a.status = 'ACTIVE'")
    @Transactional(readOnly = true)
    Optional<Account> findActiveById(@Param("id") UUID accountId);

    @Query("SELECT DISTINCT a FROM Account a LEFT JOIN FETCH a.envelopes WHERE a.id = :id AND a.status = 'ACTIVE'")
    @Transactional(readOnly = true)
    Optional<Account> findActiveByIdWithEnvelopes(@Param("id") UUID id);

}
