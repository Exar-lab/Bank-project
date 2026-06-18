package com.banco.co.user.adapter.out.jpa;

import com.banco.co.role.model.Role;
import com.banco.co.user.domain.model.User;
import com.banco.co.user.domain.model.UserSnapshot;
import com.banco.co.user.domain.port.out.IUserRepository;
import com.banco.co.user.enums.KycStatus;
import com.banco.co.user.enums.UserStatus;
import com.banco.co.user.exception.user.UserNotFoundException;
import com.banco.co.user.repository.IUserCredential;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * JPA adapter that implements the output port IUserRepository.
 * Delegates to IUserJpaRepository (Spring Data) and converts via UserEntityMapper.
 * @Transactional belongs on the service (application) layer, NOT here.
 */
@Component
public class UserJpaAdapter implements IUserRepository {

    private final IUserJpaRepository jpaRepository;
    private final UserEntityMapper mapper;
    private final IUserCredential credentialRepository;

    public UserJpaAdapter(
            IUserJpaRepository jpaRepository,
            UserEntityMapper mapper,
            IUserCredential credentialRepository) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
        this.credentialRepository = credentialRepository;
    }

    // ══════════════════════════════════════════════════════════
    //  PERSISTENCIA
    // ══════════════════════════════════════════════════════════

    @Override
    public User save(User user) {
        UserEntity entity = mapper.toEntity(user);
        // Preserve the ID when updating an existing record
        if (user.getId() != null) {
            entity.setId(user.getId());
        }
        return mapper.toDomain(jpaRepository.save(entity));
    }

    @Override
    public Optional<User> findById(UUID id) {
        return jpaRepository.findById(id).map(mapper::toDomain);
    }

    // ══════════════════════════════════════════════════════════
    //  BÚSQUEDAS PARA OPERACIONES NORMALES (Solo ACTIVE)
    // ══════════════════════════════════════════════════════════

    @Override
    public Optional<User> findActiveByEmail(String email) {
        return jpaRepository.findActiveByEmail(email).map(mapper::toDomain);
    }

    @Override
    public Optional<User> findActiveByUserCode(String userCode) {
        return jpaRepository.findActiveByUserCode(userCode).map(mapper::toDomain);
    }

    @Override
    public Optional<User> findActiveByDocumentNumber(String documentNumber) {
        return jpaRepository.findActiveByDocumentNumber(documentNumber).map(mapper::toDomain);
    }

    @Override
    public List<User> findAllActive() {
        return jpaRepository.findAllActive().stream().map(mapper::toDomain).toList();
    }

    @Override
    public Optional<User> findActiveByEmailWithCredential(String email) {
        // Domain User doesn't carry the credential — return the base user
        return jpaRepository.findActiveByEmail(email).map(mapper::toDomain);
    }

    @Override
    public Optional<User> findActiveByIdWithAccounts(UUID id) {
        // Domain User doesn't carry accounts — return the base user
        return jpaRepository.findById(id).map(mapper::toDomain);
    }

    // ══════════════════════════════════════════════════════════
    //  BÚSQUEDAS ADMINISTRATIVAS (Todos los estados)
    // ══════════════════════════════════════════════════════════

    @Override
    public Optional<User> findByEmail(String email) {
        return jpaRepository.findByEmail(email).map(mapper::toDomain);
    }

    @Override
    public List<User> findByStatus(UserStatus status) {
        return jpaRepository.findByStatus(status).stream().map(mapper::toDomain).toList();
    }

    @Override
    public List<User> findByStatusNotIn(List<UserStatus> excludedStatuses) {
        return jpaRepository.findByStatusNotIn(excludedStatuses).stream().map(mapper::toDomain).toList();
    }

    @Override
    public List<User> findAllOrderByCreatedDesc() {
        return jpaRepository.findAllOrderByCreatedDesc().stream().map(mapper::toDomain).toList();
    }

    // ══════════════════════════════════════════════════════════
    //  VERIFICACIONES DE EXISTENCIA
    // ══════════════════════════════════════════════════════════

    @Override
    public boolean existsByEmailAndStatus(String email, UserStatus status) {
        return jpaRepository.existsByEmailAndStatus(email, status);
    }

    @Override
    public boolean existsByDocumentNumberAndStatus(String documentNumber, UserStatus status) {
        return jpaRepository.existsByDocumentNumberAndStatus(documentNumber, status);
    }

    @Override
    public boolean existsByEmail(String email) {
        return jpaRepository.existsByEmail(email);
    }

    @Override
    public boolean existsByDocumentNumber(String documentNumber) {
        return jpaRepository.existsByDocumentNumber(documentNumber);
    }

    // ══════════════════════════════════════════════════════════
    //  BÚSQUEDAS POR FECHA
    // ══════════════════════════════════════════════════════════

    @Override
    public List<User> findActiveCreatedAfter(LocalDateTime date) {
        return jpaRepository.findActiveCreatedAfter(date).stream().map(mapper::toDomain).toList();
    }

    @Override
    public List<User> findByCreatedDateBefore(LocalDateTime date) {
        return jpaRepository.findByCreatedDateBefore(date).stream().map(mapper::toDomain).toList();
    }

    @Override
    public List<User> findByCreatedDateBetween(LocalDateTime start, LocalDateTime end) {
        return jpaRepository.findByCreatedDateBetween(start, end).stream().map(mapper::toDomain).toList();
    }

    // ══════════════════════════════════════════════════════════
    //  BÚSQUEDAS POR KYC
    // ══════════════════════════════════════════════════════════

    @Override
    public List<User> findActiveByKycStatus(KycStatus kycStatus) {
        return jpaRepository.findActiveByKycStatus(kycStatus).stream().map(mapper::toDomain).toList();
    }

    @Override
    public List<User> findPendingKycOlderThan(LocalDateTime before) {
        return jpaRepository.findPendingKycOlderThan(before).stream().map(mapper::toDomain).toList();
    }

    // ══════════════════════════════════════════════════════════
    //  ESTADÍSTICAS
    // ══════════════════════════════════════════════════════════

    @Override
    public long countByStatus(UserStatus status) {
        return jpaRepository.countByStatus(status);
    }

    @Override
    public long countActiveCreatedAfter(LocalDateTime date) {
        return jpaRepository.countActiveCreatedAfter(date);
    }

    // ══════════════════════════════════════════════════════════
    //  SNAPSHOT (para consumo cross-feature)
    // ══════════════════════════════════════════════════════════

    @Override
    public UserSnapshot findSnapshotByEmail(String email) {
        UserEntity entity = jpaRepository.findActiveByEmail(email)
                .orElseThrow(() -> new UserNotFoundException(email));

        User domain = mapper.toDomain(entity);

        // Resolve primary role name from credential (cross-feature via credential table)
        String roleName = credentialRepository.findByEmailWithRolesAndPermissions(email)
                .map(credential -> credential.getRoles().stream()
                        .map(Role::getName)
                        .max(Comparator.comparingInt(role -> role.getPrivilegeLevel()))
                        .map(Enum::name)
                        .orElse("CUSTOMER_BASIC"))
                .orElse("CUSTOMER_BASIC");

        return new UserSnapshot(
                domain.getId().toString(),
                domain.getEmail(),
                domain.getUsername(),
                roleName
        );
    }

    @Override
    public UserSnapshot findSnapshotByUserId(UUID userId) {
        UserEntity entity = jpaRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId.toString()));

        User domain = mapper.toDomain(entity);

        // Resolve primary role name from credential (cross-feature via credential table)
        String roleName = credentialRepository.findByEmailWithRolesAndPermissions(domain.getEmail())
                .map(credential -> credential.getRoles().stream()
                        .map(Role::getName)
                        .max(Comparator.comparingInt(role -> role.getPrivilegeLevel()))
                        .map(Enum::name)
                        .orElse("CUSTOMER_BASIC"))
                .orElse("CUSTOMER_BASIC");

        return new UserSnapshot(
                domain.getId().toString(),
                domain.getEmail(),
                domain.getUsername(),
                roleName
        );
    }
}
