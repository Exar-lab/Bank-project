package com.banco.co.account.repository;

import com.banco.co.account.model.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;
@Repository
public interface IAccountMapper extends JpaRepository<Account, UUID> {
}
