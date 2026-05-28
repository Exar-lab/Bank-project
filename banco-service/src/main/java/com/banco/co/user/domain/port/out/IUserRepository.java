package com.banco.co.user.domain.port.out;

import com.banco.co.user.domain.model.User;
import com.banco.co.user.domain.model.UserSnapshot;
import com.banco.co.user.enums.KycStatus;
import com.banco.co.user.enums.UserStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Output port — persistence contract for the user domain.
 * NO JPA imports, NO Spring Data imports.
 * Implementations live in adapter/out/jpa/.
 */
public interface IUserRepository {

    // ══════════════════════════════════════════════════════════
    //  PERSISTENCIA
    // ══════════════════════════════════════════════════════════

    User save(User user);

    Optional<User> findById(UUID id);

    // ══════════════════════════════════════════════════════════
    //  BÚSQUEDAS PARA OPERACIONES NORMALES (Solo ACTIVE)
    // ══════════════════════════════════════════════════════════

    Optional<User> findActiveByEmail(String email);

    Optional<User> findActiveByUserCode(String userCode);

    Optional<User> findActiveByDocumentNumber(String documentNumber);

    List<User> findAllActive();

    Optional<User> findActiveByEmailWithCredential(String email);

    Optional<User> findActiveByIdWithAccounts(UUID id);

    // ══════════════════════════════════════════════════════════
    //  BÚSQUEDAS ADMINISTRATIVAS (Todos los estados)
    // ══════════════════════════════════════════════════════════

    Optional<User> findByEmail(String email);

    List<User> findByStatus(UserStatus status);

    List<User> findByStatusNotIn(List<UserStatus> excludedStatuses);

    List<User> findAllOrderByCreatedDesc();

    // ══════════════════════════════════════════════════════════
    //  VERIFICACIONES DE EXISTENCIA
    // ══════════════════════════════════════════════════════════

    boolean existsByEmailAndStatus(String email, UserStatus status);

    boolean existsByDocumentNumberAndStatus(String documentNumber, UserStatus status);

    boolean existsByEmail(String email);

    boolean existsByDocumentNumber(String documentNumber);

    // ══════════════════════════════════════════════════════════
    //  BÚSQUEDAS POR FECHA
    // ══════════════════════════════════════════════════════════

    List<User> findActiveCreatedAfter(LocalDateTime date);

    List<User> findByCreatedDateBefore(LocalDateTime date);

    List<User> findByCreatedDateBetween(LocalDateTime start, LocalDateTime end);

    // ══════════════════════════════════════════════════════════
    //  BÚSQUEDAS POR KYC
    // ══════════════════════════════════════════════════════════

    List<User> findActiveByKycStatus(KycStatus kycStatus);

    List<User> findPendingKycOlderThan(LocalDateTime before);

    // ══════════════════════════════════════════════════════════
    //  ESTADÍSTICAS
    // ══════════════════════════════════════════════════════════

    long countByStatus(UserStatus status);

    long countActiveCreatedAfter(LocalDateTime date);

    // ══════════════════════════════════════════════════════════
    //  SNAPSHOT (para consumo cross-feature)
    // ══════════════════════════════════════════════════════════

    /**
     * Returns a lightweight read-only projection of the user,
     * including their primary role name. Used by other features
     * (account, card, etc.) that only need identity data.
     */
    UserSnapshot findSnapshotByEmail(String email);
}
