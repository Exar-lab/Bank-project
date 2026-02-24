package com.banco.co.user.service.user;

import com.banco.co.auditLog.enums.AuditAction;
import com.banco.co.auditLog.enums.AuditEntityType;
import com.banco.co.auditLog.service.IAuditLogService;
import com.banco.co.exception.authentication.PasswordMismatchException;
import com.banco.co.exception.authentication.UnauthorizedException;
import com.banco.co.role.configuration.RolePermissionMatrix;
import com.banco.co.role.enums.SystemRole;
import com.banco.co.role.exception.RoleLevelMismatchException;
import com.banco.co.role.model.Role;
import com.banco.co.role.service.IRoleService;
import com.banco.co.user.dto.customer.CustomerRequestDto;
import com.banco.co.user.dto.customer.CustomerResponseDto;
import com.banco.co.user.dto.customer.CustomerUpdateDto;
import com.banco.co.user.dto.customer.PasswordRequestDto;
import com.banco.co.user.dto.employee.EmployeeRequestDto;
import com.banco.co.user.dto.employee.EmployeeResponseDto;
import com.banco.co.user.enums.UserStatus;
import com.banco.co.user.exception.user.UserAlreadyExist;
import com.banco.co.user.exception.user.UserNotFoundException;
import com.banco.co.user.mapper.costumer.ICustomerMapper;
import com.banco.co.user.mapper.employee.IEmployeeMapper;
import com.banco.co.user.model.User;
import com.banco.co.user.model.UserCredential;
import com.banco.co.user.repository.IUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.Objects;
import java.util.UUID;

@RequiredArgsConstructor
@Slf4j
@Service
public class UserService implements IUserService {

        private final IUserRepository userRepository;
        private final IRoleService roleService;
        private final BCryptPasswordEncoder bCryptPasswordEncoder;
        private final ICustomerMapper customerMapper;
        private final IEmployeeMapper employeeMapper;
        private final IAuditLogService auditLogService;

        // ══════════════════════════════════════════════════════════
        // AUTO-REGISTRO PÚBLICO (Cliente)
        // ══════════════════════════════════════════════════════════

        /**
         * Registro público de cliente - Siempre CUSTOMER_BASIC
         * NO requiere autenticación
         */
        @Transactional
        @Override
        public CustomerResponseDto createUser(CustomerRequestDto dto) {

                // Validar email
                if (userRepository.existsByEmail(dto.email())) {

                        // ⚠️ AUDITAR: Intento de registro con email existente
                        auditLogService.logAnonymous(
                                        AuditAction.CREATE_PROFILE_FAILED,
                                        AuditEntityType.USER,
                                        null,
                                        String.format("Registration attempt with existing email: %s", dto.email()),
                                        null,
                                        null);

                        throw new UserAlreadyExist(dto.email());
                }

                // Validar documento
                if (userRepository.existsByDocumentNumber(dto.documentNumber())) {

                        // ⚠️ AUDITAR: Intento con documento existente
                        auditLogService.logAnonymous(
                                        AuditAction.CREATE_PROFILE_FAILED,
                                        AuditEntityType.USER,
                                        null,
                                        String.format("Registration attempt with existing document number: %s",
                                                        dto.documentNumber()),
                                        null,
                                        null);

                        throw new UserAlreadyExist(dto.documentNumber());
                }

                // Crear User
                User user = customerMapper.toEntity(dto);
                user.setStatus(UserStatus.ACTIVE);

                // Crear Credential
                UserCredential credential = new UserCredential();
                credential.setEmail(dto.email());
                credential.setPasswordHash(bCryptPasswordEncoder.encode(dto.password()));

                // Asignar rol CUSTOMER_BASIC (siempre)
                Role role = roleService.findRoleByName(SystemRole.CUSTOMER_BASIC);
                credential.getRoles().add(role);

                // Enlazar
                credential.setUser(user);
                user.setCredential(credential);

                // Guardar
                User savedUser = userRepository.save(user);

                log.info("User created: {} ({})", savedUser.getUserCode(), savedUser.getEmail());

                // ✅ AUDITAR: Registro exitoso
                auditLogService.logAnonymous(
                                AuditAction.CREATE_PROFILE,
                                AuditEntityType.USER,
                                savedUser.getId().toString(),
                                String.format("User %s registered successfully", savedUser.getEmail()),
                                null,
                                null);

                return customerMapper.toDto(savedUser);
        }

        // ══════════════════════════════════════════════════════════
        // CREACIÓN DE EMPLEADO (Solo Admin)
        // ══════════════════════════════════════════════════════════

        /**
         * Admin crea empleado
         * Requiere permiso 'user:create' + validación de jerarquía
         */
        @Transactional
        @Override
        public EmployeeResponseDto createUserByEmployee(String creatorEmail, EmployeeRequestDto dto) {

                User userCreator = userRepository.findActiveByEmailWithCredential(creatorEmail)
                                .orElseThrow(() -> new UserNotFoundException(creatorEmail));

                // Obtener el rol de MAYOR privilegio del creador
                SystemRole highestRole = userCreator.getCredential().getRoles().stream()
                                .map(Role::getName)
                                .max(Comparator.comparingInt(SystemRole::getPrivilegeLevel))
                                .orElseThrow(() -> new IllegalStateException("User has no roles"));

                // Validar que PUEDE asignar el rol solicitado
                if (!RolePermissionMatrix.canAssignRole(highestRole, dto.role())) {
                        auditLogService.logFailure(
                                        userCreator,
                                        AuditAction.CREATE_PROFILE_FAILED,
                                        AuditEntityType.USER,
                                        String.format("User %s (role %s) cannot assign role %s",
                                                        creatorEmail, highestRole, dto.role()));
                        throw new RoleLevelMismatchException(dto.role().name());
                }

                // Validar email
                if (userRepository.existsByEmail(dto.email())) {

                        // ⚠️ AUDITAR: Intento de registro con email existente
                        auditLogService.logAnonymous(
                                        AuditAction.CREATE_PROFILE_FAILED,
                                        AuditEntityType.USER,
                                        null,
                                        String.format("Registration employee attempt with existing email: %s",
                                                        dto.email()),
                                        null,
                                        null);

                        throw new UserAlreadyExist(dto.email());
                }

                // Validar documento
                if (userRepository.existsByDocumentNumber(dto.documentNumber())) {

                        // ⚠️ AUDITAR: Intento con documento existente
                        auditLogService.logAnonymous(
                                        AuditAction.CREATE_PROFILE_FAILED,
                                        AuditEntityType.USER,
                                        null,
                                        String.format("Registration employee attempt with existing document number: %s",
                                                        dto.documentNumber()),
                                        null,
                                        null);

                        throw new UserAlreadyExist(dto.documentNumber());
                }

                // Crear User
                User user = employeeMapper.toEntity(dto);
                user.setStatus(UserStatus.ACTIVE);

                // Crear Credential
                UserCredential credential = new UserCredential();
                credential.setEmail(dto.email());
                credential.setPasswordHash(bCryptPasswordEncoder.encode(dto.password()));

                // Asignar rol solicitado
                Role role = roleService.findRoleByName(dto.role());
                credential.getRoles().add(role);

                // Enlazar
                credential.setUser(user);
                user.setCredential(credential);

                // Guardar
                User savedUser = userRepository.save(user);

                log.info("Employee created: {} ({})", savedUser.getUserCode(), savedUser.getEmail());

                // ✅ AUDITAR: Registro exitoso
                auditLogService.logSuccess(
                                userCreator,
                                AuditAction.EMPLOYEE_CREATED,
                                AuditEntityType.USER,
                                savedUser.getId().toString(),
                                String.format("Admin %s created employee %s with role %s",
                                                creatorEmail, savedUser.getEmail(), dto.role()),
                                null,
                                null);

                return employeeMapper.toDto(savedUser);
        }

        // ══════════════════════════════════════════════════════════
        // OPERACIONES PROPIAS (Usuario autenticado)
        // ══════════════════════════════════════════════════════════

        /**
         * Obtener perfil del usuario autenticado
         */
        @Override
        public CustomerResponseDto findUserByEmail(String email) {
                User user = getEntityUserByEmail(email);
                log.info("User found: {} ({})", user.getEmail(), user.getDocumentNumber());
                return customerMapper.toDto(user);
        }

        /**
         * Actualizar perfil del usuario autenticado
         */
        @Transactional
        @Override
        public CustomerResponseDto updateUser(String email, CustomerUpdateDto updates) {

                User user = getEntityUserByEmail(email);

                // Guardar valores antiguos para auditoría
                String oldValues = customerMapper.toJsonString(user);

                customerMapper.updateEntityFromDto(updates, user);
                userRepository.save(user);

                String newValues = customerMapper.toJsonString(user);

                log.info("User updated: {} ({})", user.getEmail(), user.getDocumentNumber());

                // ✅ AUDITAR: Actualización exitosa
                auditLogService.logSuccess(
                                user,
                                AuditAction.UPDATE_PROFILE,
                                AuditEntityType.USER,
                                user.getId().toString(),
                                String.format("User %s updated profile", user.getEmail()),
                                oldValues,
                                newValues);

                return customerMapper.toDto(user);
        }

        /**
         * Cambiar password del usuario autenticado
         */
        @Transactional
        @Override
        public void updatePassword(PasswordRequestDto dto, String email) {

                User user = userRepository.findActiveByEmailWithCredential(email)
                                .orElseThrow(() -> new UserNotFoundException(email));

                // Validar passwords coincidan
                if (!Objects.equals(dto.password(), dto.confirmPassword())) {

                        auditLogService.logFailure(
                                        user,
                                        AuditAction.PASSWORD_CHANGE_FAILED,
                                        AuditEntityType.USER,
                                        "Password and confirmation do not match");

                        throw new PasswordMismatchException();
                }

                // Validar password actual
                if (!bCryptPasswordEncoder.matches(dto.currentPassword(), user.getCredential().getPasswordHash())) {
                        auditLogService.logFailure(
                                        user,
                                        AuditAction.PASSWORD_CHANGE_FAILED,
                                        AuditEntityType.USER,
                                        "Current password do not match");
                        throw new PasswordMismatchException();
                }

                // Actualizar
                user.getCredential().setPasswordHash(bCryptPasswordEncoder.encode(dto.password()));
                user.getCredential().setLastPasswordChange(LocalDateTime.now());
                userRepository.save(user);

                log.info("Password changed: {} ({})", user.getEmail(), user.getDocumentNumber());

                // ✅ AUDITAR: Cambio de password exitoso (CRÍTICO)
                auditLogService.logSuccess(
                                user,
                                AuditAction.PASSWORD_CHANGED,
                                AuditEntityType.SECURITY,
                                user.getId().toString(),
                                "Password changed successfully",
                                null,
                                null);
        }

        /**
         * Soft delete del usuario autenticado
         */
        @Transactional
        @Override
        public void deleteUserByEmail(String email) {
                User user = getEntityUserByEmail(email);
                user.setStatus(UserStatus.DELETED);
                userRepository.save(user);

                log.info("User deleted: {} ({})", user.getEmail(), user.getDocumentNumber());

                // ✅ AUDITAR: Borrado de cuenta (soft delete)
                auditLogService.logSuccess(
                                user,
                                AuditAction.DELETE_PROFILE,
                                AuditEntityType.USER,
                                user.getId().toString(),
                                String.format("User %s deleted account", user.getEmail()),
                                null,
                                null);
        }

        // ══════════════════════════════════════════════════════════
        // OPERACIONES ADMINISTRATIVAS
        // ══════════════════════════════════════════════════════════

        /**
         * Admin obtiene usuario por ID (cualquier usuario)
         */
        @Override
        public CustomerResponseDto getUserById(UUID userId, String adminEmail) {
                User admin = getEntityUserByEmail(adminEmail);
                User user = userRepository.findById(userId)
                                .orElseThrow(() -> new UserNotFoundException(userId.toString()));

                // Auditar que admin vio el perfil
                auditLogService.logSuccess(
                                admin,
                                AuditAction.USER_READ_BY_ADMIN,
                                AuditEntityType.USER,
                                userId.toString(),
                                String.format("Admin %s viewed user %s", adminEmail, user.getEmail()),
                                null,
                                null);

                return customerMapper.toDto(user);
        }

        /**
         * Admin actualiza usuario (cualquier usuario)
         */
        @Transactional
        @Override
        public CustomerResponseDto updateUserByAdmin(
                        UUID userId,
                        CustomerUpdateDto dto,
                        String adminEmail) {
                User admin = getEntityUserByEmail(adminEmail);
                User user = userRepository.findById(userId)
                                .orElseThrow(() -> new UserNotFoundException(userId.toString()));

                // Validar jerarquía
                validateAdminCanModifyUser(admin, user);

                // Guardar valores antiguos
                String oldValues = customerMapper.toJsonString(user);

                // Actualizar
                customerMapper.updateEntityFromDto(dto, user);
                userRepository.save(user);

                String newValues = customerMapper.toJsonString(user);

                auditLogService.logSuccess(
                                admin,
                                AuditAction.USER_UPDATED_BY_ADMIN,
                                AuditEntityType.USER,
                                userId.toString(),
                                String.format("Admin %s updated user %s", adminEmail, user.getEmail()),
                                oldValues,
                                newValues);

                return customerMapper.toDto(user);
        }

        /**
         * Admin suspende usuario
         */
        @Transactional
        @Override
        public void suspendUser(UUID userId, String reason, String adminEmail) {
                User admin = getEntityUserByEmail(adminEmail);
                User user = userRepository.findById(userId)
                                .orElseThrow(() -> new UserNotFoundException(userId.toString()));

                // Validar jerarquía
                validateAdminCanModifyUser(admin, user);

                user.setStatus(UserStatus.SUSPENDED);
                userRepository.save(user);

                auditLogService.logSuccess(
                                admin,
                                AuditAction.USER_SUSPENDED,
                                AuditEntityType.USER,
                                userId.toString(),
                                String.format("Admin %s suspended user %s. Reason: %s",
                                                adminEmail, user.getEmail(), reason),
                                null,
                                null);

                log.warn("User {} suspended by admin {}. Reason: {}",
                                user.getEmail(), adminEmail, reason);
        }

        /**
         * Admin reactiva usuario suspendido
         */
        @Transactional
        @Override
        public void activateUser(UUID userId, String adminEmail) {
                User admin = getEntityUserByEmail(adminEmail);
                User user = userRepository.findById(userId)
                                .orElseThrow(() -> new UserNotFoundException(userId.toString()));

                // Validar jerarquía
                validateAdminCanModifyUser(admin, user);

                user.setStatus(UserStatus.ACTIVE);
                userRepository.save(user);

                auditLogService.logSuccess(
                                admin,
                                AuditAction.USER_ACTIVATED,
                                AuditEntityType.USER,
                                userId.toString(),
                                String.format("Admin %s activated user %s", adminEmail, user.getEmail()),
                                null,
                                null);

                log.info("User {} activated by admin {}", user.getEmail(), adminEmail);
        }

        /**
         * Admin cambia status de usuario
         */
        @Transactional
        @Override
        public CustomerResponseDto updateUserStatus(UUID userId, UserStatus status, String adminEmail) {
                User admin = getEntityUserByEmail(adminEmail);
                User user = userRepository.findById(userId)
                                .orElseThrow(() -> new UserNotFoundException(userId.toString()));

                // Validar jerarquía
                validateAdminCanModifyUser(admin, user);

                UserStatus oldStatus = user.getStatus();
                user.setStatus(status);
                userRepository.save(user);

                auditLogService.logSuccess(
                                admin,
                                AuditAction.USER_STATUS_CHANGED,
                                AuditEntityType.USER,
                                userId.toString(),
                                String.format("Admin %s changed user %s status from %s to %s",
                                                adminEmail, user.getEmail(), oldStatus, status),
                                oldStatus.toString(),
                                status.toString());

                log.info("User {} status changed from {} to {} by admin {}",
                                user.getEmail(), oldStatus, status, adminEmail);

                return customerMapper.toDto(user);
        }

        // ══════════════════════════════════════════════════════════
        // MÉTODOS AUXILIARES
        // ══════════════════════════════════════════════════════════
        @Override
        public User getEntityUserByEmail(String email) {
                return userRepository.findActiveByEmail(email)
                                .orElseThrow(() -> new UserNotFoundException(email));
        }
        @Override
        public User getEntityUserByDocumentNumber(String documentNumber) {
                return userRepository.findActiveByDocumentNumber(documentNumber)
                                .orElseThrow(() -> new UserNotFoundException(documentNumber));
        }

        private SystemRole getHighestRole(User user) {
                return user.getCredential().getRoles().stream()
                                .map(Role::getName)
                                .max(Comparator.comparingInt(SystemRole::getPrivilegeLevel))
                                .orElse(SystemRole.CUSTOMER_BASIC);
        }

        private void validateAdminCanModifyUser(User admin, User target) {
                SystemRole adminRole = getHighestRole(admin);
                SystemRole targetRole = getHighestRole(target);

                if (targetRole.getPrivilegeLevel() >= adminRole.getPrivilegeLevel()) {
                        throw new UnauthorizedException(
                                        "Cannot modify user with equal or higher privilege level");
                }
        }

}
