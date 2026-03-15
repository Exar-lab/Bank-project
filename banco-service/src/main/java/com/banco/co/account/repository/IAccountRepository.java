package com.banco.co.account.repository;

import com.banco.co.account.enums.AccountType;
import com.banco.co.account.model.Account;
import com.banco.co.user.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
@Repository
public interface IAccountRepository extends JpaRepository<Account, UUID> {

    Optional<Account> findFirstActiveAccountByUser_Email(String userEmail);

    Optional<Account> findFirstActiveAccountByUser_DocumentNumber(String userDocumentNumber);

    Optional<Account> findActiveAccountByAccountCode(String accountCode);

    List<Account> findActiveAccountsByUser_Email(String userEmail);

    boolean existsByUser_EmailAndAccountType(String userEmail, AccountType accountType);

    List<Account> findAllByCreatedAtAfter(LocalDateTime date);
    @Query("SELECT a FROM Account a " +
            "LEFT JOIN FETCH a.user u " +
            "WHERE a.accountCode = :accountCode")
    Optional<Account> findAccountWithUser(@Param("accountCode") String accountCode);

    String user(User user);

    Optional<Account> findActiveById(UUID accountId);

}
