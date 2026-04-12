package com.banco.co.user.repository;

import com.banco.co.user.model.UserCredential;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;
@Repository
public interface IUserCredential extends JpaRepository<UserCredential, UUID> {
    @Transactional(readOnly = true)
    @Query("""
            SELECT DISTINCT uc
            FROM UserCredential uc
            LEFT JOIN FETCH uc.user u
            LEFT JOIN FETCH uc.roles r
            LEFT JOIN FETCH r.permissions p
            WHERE uc.email = :email
            """)
    Optional<UserCredential> findByEmailWithRolesAndPermissions(@Param("email") String email);
}
