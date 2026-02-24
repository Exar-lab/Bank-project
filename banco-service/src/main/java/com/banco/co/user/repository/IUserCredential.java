package com.banco.co.user.repository;

import com.banco.co.user.model.UserCredential;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;
@Repository
public interface IUserCredential extends JpaRepository<UserCredential, UUID> {
    Optional<UserCredential> findByEmail(String email);
}
