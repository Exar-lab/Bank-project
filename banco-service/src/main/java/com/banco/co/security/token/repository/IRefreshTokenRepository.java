package com.banco.co.security.token.repository;

import com.banco.co.security.token.enums.RefreshTokenRevocationReason;
import com.banco.co.security.token.model.RefreshToken;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
@Repository
public interface IRefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    @Transactional(readOnly = true)
    Optional<RefreshToken> findByJti(String jti);

    @Transactional(readOnly = true)
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT rt FROM RefreshToken rt WHERE rt.jti = :jti")
    Optional<RefreshToken> findByJtiForUpdate(@Param("jti") String jti);

    @Transactional(readOnly = true)
    List<RefreshToken> findByUserCredential_IdAndRevokedFalse(UUID userId);

    @Modifying
    @Query("DELETE FROM RefreshToken rt WHERE rt.expiresAt < :now")
    void deleteExpiredTokens(@Param("now") LocalDateTime now);

    @Modifying
    @Query("""
            UPDATE RefreshToken rt
            SET rt.revoked = true,
                rt.revokedAt = :revokedAt,
                rt.revocationReason = :reason,
                rt.replacedByJti = :replacedByJti
            WHERE rt.id = :tokenId
            """)
    int revokeById(@Param("tokenId") UUID tokenId,
                   @Param("reason") RefreshTokenRevocationReason reason,
                   @Param("revokedAt") LocalDateTime revokedAt,
                   @Param("replacedByJti") String replacedByJti);

    @Modifying
    @Query("""
            UPDATE RefreshToken rt
            SET rt.revoked = true,
                rt.revokedAt = :revokedAt,
                rt.revocationReason = :reason
            WHERE rt.userCredential.id = :userId
              AND rt.revoked = false
            """)
    int revokeAllActiveByUser(@Param("userId") UUID userId,
                              @Param("reason") RefreshTokenRevocationReason reason,
                              @Param("revokedAt") LocalDateTime revokedAt);


}
