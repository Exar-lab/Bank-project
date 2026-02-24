package com.banco.co.permission.repository;

import com.banco.co.permission.enums.SystemPermission;
import com.banco.co.permission.model.Permission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.lang.ScopedValue;
import java.util.Optional;
import java.util.UUID;
@Repository
public interface IPermissionRepository extends JpaRepository<Permission, UUID> {
    boolean existsByName(SystemPermission name);

    Optional<Permission> findByName(SystemPermission name);

}
