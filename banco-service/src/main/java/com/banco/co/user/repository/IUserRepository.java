package com.banco.co.user.repository;

import com.banco.co.user.enums.KycStatus;
import com.banco.co.user.enums.UserStatus;
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
public interface IUserRepository extends JpaRepository<User, UUID> {
    // ══════════════════════════════════════════════════════════
    //  BÚSQUEDAS PARA OPERACIONES NORMALES (Solo ACTIVE)
    // ══════════════════════════════════════════════════════════

    // Para login, perfil, operaciones cotidianas
    @Query("SELECT u FROM User u WHERE u.email = :email AND u.status = 'ACTIVE'")
    Optional<User> findActiveByEmail(@Param("email") String email);

    @Query("SELECT u FROM User u WHERE u.userCode = :userCode AND u.status = 'ACTIVE'")
    Optional<User> findActiveByUserCode(@Param("userCode") String userCode);

    @Query("SELECT u FROM User u WHERE u.documentNumber = :documentNumber AND u.status = 'ACTIVE'")
    Optional<User> findActiveByDocumentNumber(@Param("documentNumber") String documentNumber);

    // Listar usuarios activos (para dropdown, listados públicos)
    @Query("SELECT u FROM User u WHERE u.status = 'ACTIVE' ORDER BY u.createdDate DESC")
    List<User> findAllActive();

    // ══════════════════════════════════════════════════════════
    //  BÚSQUEDAS ADMINISTRATIVAS (Todos los estados)
    // ══════════════════════════════════════════════════════════

    // Buscar por email sin importar el estado (para admin)
    Optional<User> findByEmail(String email);

    // Buscar por cualquier estado (para reportes, administración)
    List<User> findByStatus(UserStatus status);

    // Buscar excluyendo estados específicos
    @Query("SELECT u FROM User u WHERE u.status NOT IN :excludedStatuses")
    List<User> findByStatusNotIn(@Param("excludedStatuses") List<UserStatus> excludedStatuses);

    // Todos los usuarios (con paginación en el servicio)
    @Query("SELECT u FROM User u ORDER BY u.createdDate DESC")
    List<User> findAllOrderByCreatedDesc();

    // ══════════════════════════════════════════════════════════
    //  VERIFICACIONES DE EXISTENCIA
    // ══════════════════════════════════════════════════════════

    // Para validaciones de unicidad (solo usuarios activos)
    boolean existsByEmailAndStatus(String email, UserStatus status);

    boolean existsByDocumentNumberAndStatus(String documentNumber, UserStatus status);

    // Para verificar si existe sin importar estado (admin)
    boolean existsByEmail(String email);

    boolean existsByDocumentNumber(String documentNumber);

    // ══════════════════════════════════════════════════════════
    //  BÚSQUEDAS POR FECHA (Para reportes y análisis)
    // ══════════════════════════════════════════════════════════

    // Usuarios registrados después de una fecha (activos)
    @Query("SELECT u FROM User u WHERE u.createdDate > :date AND u.status = 'ACTIVE'")
    List<User> findActiveCreatedAfter(@Param("date") LocalDateTime date);

    // Usuarios registrados antes de una fecha (todos)
    List<User> findByCreatedDateBefore(LocalDateTime date);

    List<User> findByCreatedDateBetween(LocalDateTime start, LocalDateTime end);

    // ══════════════════════════════════════════════════════════
    //  BÚSQUEDAS POR KYC (Know Your Customer)
    // ══════════════════════════════════════════════════════════

    @Query("SELECT u FROM User u WHERE u.kycStatus = :kycStatus AND u.status = 'ACTIVE'")
    List<User> findActiveByKycStatus(@Param("kycStatus") KycStatus kycStatus);

    // Usuarios pendientes de verificación (para compliance)
    @Query("SELECT u FROM User u WHERE u.kycStatus = 'PENDING' AND u.status = 'ACTIVE' " +
            "AND u.createdDate < :before")
    List<User> findPendingKycOlderThan(@Param("before") LocalDateTime before);

    // ══════════════════════════════════════════════════════════
    //  ESTADÍSTICAS Y REPORTES
    // ══════════════════════════════════════════════════════════

    @Query("SELECT COUNT(u) FROM User u WHERE u.status = :status")
    long countByStatus(@Param("status") UserStatus status);

    @Query("SELECT COUNT(u) FROM User u WHERE u.createdDate > :date AND u.status = 'ACTIVE'")
    long countActiveCreatedAfter(@Param("date") LocalDateTime date);

    // Usuarios por tipo de documento
    @Query("SELECT u.documentType, COUNT(u) FROM User u " +
            "WHERE u.status = 'ACTIVE' GROUP BY u.documentType")
    List<Object[]> countActiveByDocumentType();

    // ══════════════════════════════════════════════════════════
    //  BÚSQUEDAS CON JOINS (Optimizadas)
    // ══════════════════════════════════════════════════════════

    // Usuario con credenciales (para autenticación)
    @Query("SELECT u FROM User u " +
            "LEFT JOIN FETCH u.credential c " +
            "WHERE u.email = :email AND u.status = 'ACTIVE'")
    Optional<User> findActiveByEmailWithCredential(@Param("email") String email);

    // Usuario con cuentas (para dashboard)
    @Query("SELECT u FROM User u " +
            "LEFT JOIN FETCH u.accounts a " +
            "WHERE u.id = :id AND u.status = 'ACTIVE'")
    Optional<User> findActiveByIdWithAccounts(@Param("id") UUID id);
}
