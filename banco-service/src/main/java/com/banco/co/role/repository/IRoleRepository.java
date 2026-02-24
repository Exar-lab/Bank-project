package com.banco.co.role.repository;

import com.banco.co.role.enums.SystemRole;
import com.banco.co.role.model.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;
@Repository
public interface IRoleRepository extends JpaRepository<Role, UUID> {
    boolean existsByName(SystemRole sr);

    Optional<Role> findByName(SystemRole name);
}
