package com.banco.co.role.configuration.initializer;

import com.banco.co.permission.enums.SystemPermission;
import com.banco.co.permission.model.Permission;
import com.banco.co.permission.repository.IPermissionRepository;
import com.banco.co.role.configuration.RolePermissionMatrix;
import com.banco.co.role.enums.SystemRole;
import com.banco.co.role.model.Role;
import com.banco.co.role.repository.IRoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.stream.Collectors;

@Component
@Slf4j
@RequiredArgsConstructor
public class RolePermissionInitializer implements ApplicationRunner {

    private final IRoleRepository roleRepository;

    private final IPermissionRepository permissionRepository;

    @Override
    @Transactional
    public void run(@NonNull ApplicationArguments args) {
        log.info("Initializing roles and permissions...");

        // Crear permisos del sistema si no existen
        for (SystemPermission sp : SystemPermission.values()) {
            if (!permissionRepository.existsByName(sp)) {
                Permission permission = new Permission();
                permission.setName(sp);
                permission.setScope(sp.getScope());

                // Extraer resource y action del scope
                String[] parts = sp.getScope().split(":");
                permission.setResource(parts[0]);
                permission.setAction(parts[parts.length - 1]);
                permission.setDescription(sp.getDescription());
                permission.setSystemDefined(true);

                permissionRepository.save(permission);
                log.info("Created permission: {}", sp.getScope());
            }
        }

        // Crear roles del sistema si no existen
        for (SystemRole sr : SystemRole.values()) {
            if (!roleRepository.existsByName(sr)) {
                Role role = new Role();
                role.setName(sr);
                role.setDescription(sr.getDescription());
                role.setPrivilegeLevel(sr.getPrivilegeLevel());
                role.setSystemDefined(true);

                // Asignar permisos según la matriz
                Set<SystemPermission> permissionNames =
                        RolePermissionMatrix.getPermissionsFor(sr);

                Set<Permission> permissions = permissionNames.stream()
                        .map(p -> permissionRepository.findByName(p).orElseThrow())
                        .collect(Collectors.toSet());

                role.setPermissions(permissions);
                roleRepository.save(role);

                log.info("Created role: {} with {} permissions",
                        sr.name(), permissions.size());
            }
        }

        log.info("Roles and permissions initialized successfully");
    }
}