package com.banco.co.Bank.repository;

import com.banco.co.Bank.model.Bank;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;
@Repository
public interface IBankRepository extends JpaRepository<Bank, UUID> {
}
