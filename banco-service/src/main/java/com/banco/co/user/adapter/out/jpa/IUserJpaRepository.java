package com.banco.co.user.adapter.out.jpa;

import com.banco.co.user.enums.KycStatus;
import com.banco.co.user.enums.UserStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for UserEntity.
 * Package-private: only UserJpaAdapter should access this directly.
 */
interface IUserJpaRepository extends JpaRepository<UserEntity, UUID> {

    @Query("SELECT u FROM UserEntity u WHERE u.email = :email AND u.status = 'ACTIVE'")
    Optional<UserEntity> findActiveByEmail(@Param("email") String email);

    @Query("SELECT u FROM UserEntity u WHERE u.userCode = :userCode AND u.status = 'ACTIVE'")
    Optional<UserEntity> findActiveByUserCode(@Param("userCode") String userCode);

    @Query("SELECT u FROM UserEntity u WHERE u.documentNumber = :documentNumber AND u.status = 'ACTIVE'")
    Optional<UserEntity> findActiveByDocumentNumber(@Param("documentNumber") String documentNumber);

    @Query("SELECT u FROM UserEntity u WHERE u.status = 'ACTIVE' ORDER BY u.createdDate DESC")
    List<UserEntity> findAllActive();

    Optional<UserEntity> findByEmail(String email);

    List<UserEntity> findByStatus(UserStatus status);

    @Query("SELECT u FROM UserEntity u WHERE u.status NOT IN :excludedStatuses")
    List<UserEntity> findByStatusNotIn(@Param("excludedStatuses") List<UserStatus> excludedStatuses);

    @Query("SELECT u FROM UserEntity u ORDER BY u.createdDate DESC")
    List<UserEntity> findAllOrderByCreatedDesc();

    boolean existsByEmailAndStatus(String email, UserStatus status);

    boolean existsByDocumentNumberAndStatus(String documentNumber, UserStatus status);

    boolean existsByEmail(String email);

    boolean existsByDocumentNumber(String documentNumber);

    @Query("SELECT u FROM UserEntity u WHERE u.createdDate > :date AND u.status = 'ACTIVE'")
    List<UserEntity> findActiveCreatedAfter(@Param("date") LocalDateTime date);

    List<UserEntity> findByCreatedDateBefore(LocalDateTime date);

    List<UserEntity> findByCreatedDateBetween(LocalDateTime start, LocalDateTime end);

    @Query("SELECT u FROM UserEntity u WHERE u.kycStatus = :kycStatus AND u.status = 'ACTIVE'")
    List<UserEntity> findActiveByKycStatus(@Param("kycStatus") KycStatus kycStatus);

    @Query("SELECT u FROM UserEntity u WHERE u.kycStatus = 'PENDING' AND u.status = 'ACTIVE' " +
            "AND u.createdDate < :before")
    List<UserEntity> findPendingKycOlderThan(@Param("before") LocalDateTime before);

    @Query("SELECT COUNT(u) FROM UserEntity u WHERE u.status = :status")
    long countByStatus(@Param("status") UserStatus status);

    @Query("SELECT COUNT(u) FROM UserEntity u WHERE u.createdDate > :date AND u.status = 'ACTIVE'")
    long countActiveCreatedAfter(@Param("date") LocalDateTime date);
}
