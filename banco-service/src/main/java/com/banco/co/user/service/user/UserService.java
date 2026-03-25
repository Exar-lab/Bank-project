package com.banco.co.user.service.user;

import com.banco.co.auditLog.enums.AuditAction;
import com.banco.co.auditLog.enums.AuditEntityType;
import com.banco.co.auditLog.model.AuditLogDetail;
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
import com.banco.co.outbox.enums.KafkaTopic;
import com.banco.co.outbox.model.OutboxEvent;
import com.banco.co.outbox.port.IOutboxEventPort;
import com.banco.co.user.repository.IUserRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
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
        private final IOutboxEventPort outboxEventPort;
        private final ObjectMapper objectMapper;

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
                if (userRepository.existsByEmail(dto.email())){
                        // ⚠️ AUDITAR: Intento de registro con email existente
                        auditLogService.logAnonymous(
                                        AuditAction.CREATE_PROFILE_FAILED,
                                        AuditEntityType.USER,
                                        null,
                                        List.of(
                                                new AuditLogDetail("message", "Registration attempt with existing email"),
                                                new AuditLogDetail("email", dto.email())
                                        ));

                        throw new UserAlreadyExist(dto.email());
                }

                // Validar documento
                if (userRepository.existsByDocumentNumber(dto.documentNumber())) {

                        // ⚠️ AUDITAR: Intento con documento existente
                        auditLogService.logAnonymous(
                                        AuditAction.CREATE_PROFILE_FAILED,
                                        AuditEntityType.USER,
                                        null,
                                        List.of(
                                                new AuditLogDetail("message", "Registration attempt with existing document number"),
                                                new AuditLogDetail("documentNumber", dto.documentNumber())
                                        ));

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
                                List.of(
                                        new AuditLogDetail("message", "User registered successfully"),
                                        new AuditLogDetail("email", savedUser.getEmail())
                                ));

                publishUserEvent(savedUser.getId().toString(), "UserCreated", Map.of(
                        "userId", savedUser.getId().toString(),
                        "email", savedUser.getEmail(),
                        "userCode", savedUser.getUserCode()
                ));

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
                                        List.of(
                                                new AuditLogDetail("message", "User cannot assign role"),
                                                new AuditLogDetail("creatorEmail", creatorEmail),
                                                new AuditLogDetail("highestRole", highestRole),
                                                new AuditLogDetail("assignedRole", dto.role())
                                        ));
                        throw new RoleLevelMismatchException(dto.role().name());
                }

                // Validar email
                if (userRepository.existsByEmail(dto.email())) {

                        // ⚠️ AUDITAR: Intento de registro con email existente
                        auditLogService.logAnonymous(
                                        AuditAction.CREATE_PROFILE_FAILED,
                                        AuditEntityType.USER,
                                        null,
                                        List.of(
                                                new AuditLogDetail("message", "Registration employee attempt with existing email"),
                                                new AuditLogDetail("email", dto.email())
                                        ));

                        throw new UserAlreadyExist(dto.email());
                }

                // Validar documento
                if (userRepository.existsByDocumentNumber(dto.documentNumber())) {

                        // ⚠️ AUDITAR: Intento con documento existente
                        auditLogService.logAnonymous(
                                        AuditAction.CREATE_PROFILE_FAILED,
                                        AuditEntityType.USER,
                                        null,
                                        List.of(
                                                new AuditLogDetail("message", "Registration employee attempt with existing document number"),
                                                new AuditLogDetail("documentNumber", dto.documentNumber())
                                        ));

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
                                List.of(
                                        new AuditLogDetail("message", "Admin created employee"),
                                        new AuditLogDetail("adminEmail", creatorEmail),
                                        new AuditLogDetail("employeeEmail", savedUser.getEmail()),
                                        new AuditLogDetail("role", dto.role())
                                ));

                publishUserEvent(savedUser.getId().toString(), "EmployeeCreated", Map.of(
                        "userId", savedUser.getId().toString(),
                        "email", savedUser.getEmail(),
                        "role", dto.role().name()
                ));

                return employeeMapper.toDto(savedUser);
        }

        // ══════════════════════════════════════════════════════════
        // OPERACIONES PROPIAS (Usuario autenticado)
        // ══════════════════════════════════════════════════════════

        /**
         * Obtener perfil del usuario autenticado
         */
        @Transactional(readOnly = true)
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
                                List.of(
                                        new AuditLogDetail("message", "User updated profile"),
                                        new AuditLogDetail("email", user.getEmail()),
                                        new AuditLogDetail("oldValues", oldValues),
                                        new AuditLogDetail("newValues", newValues)
                                )
                );

                publishUserEvent(user.getId().toString(), "UserUpdated", Map.of(
                        "userId", user.getId().toString(),
                        "email", user.getEmail()
                ));

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
                                        List.of(
                                                new AuditLogDetail("message", "Password and confirmation do not match")
                                        ));

                        throw new PasswordMismatchException();
                }

                // Validar password actual
                if (!bCryptPasswordEncoder.matches(dto.currentPassword(), user.getCredential().getPasswordHash())) {
                        auditLogService.logFailure(
                                        user,
                                        AuditAction.PASSWORD_CHANGE_FAILED,
                                        AuditEntityType.USER,
                                        List.of(
                                                new AuditLogDetail("message", "Current password do not match")
                                        ));
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
                                List.of(
                                        new AuditLogDetail("message", "Password changed successfully")
                                ));

                // SECURITY: payload must NOT contain password, hash, or any credential
                publishUserEvent(user.getId().toString(), "PasswordChanged", Map.of(
                        "userId", user.getId().toString(),
                        "email", user.getEmail(),
                        "passwordChanged", true
                ));
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
                                List.of(
                                        new AuditLogDetail("message", "User deleted account"),
                                        new AuditLogDetail("email", user.getEmail())
                                ));

                publishUserEvent(user.getId().toString(), "UserDeleted", Map.of(
                        "userId", user.getId().toString(),
                        "email", user.getEmail()
                ));
        }

        // ══════════════════════════════════════════════════════════
        // OPERACIONES ADMINISTRATIVAS
        // ══════════════════════════════════════════════════════════

        /**
         * Admin obtiene usuario por ID (cualquier usuario)
         */
        @Transactional(readOnly = true)
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
                                List.of(
                                        new AuditLogDetail("message", "Admin viewed user"),
                                        new AuditLogDetail("adminEmail", adminEmail),
                                        new AuditLogDetail("userEmail", user.getEmail())
                                ));

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
                                List.of(
                                        new AuditLogDetail("message", "Admin updated user"),
                                        new AuditLogDetail("adminEmail", adminEmail),
                                        new AuditLogDetail("userEmail", user.getEmail()),
                                        new AuditLogDetail("oldValues", oldValues),
                                        new AuditLogDetail("newValues", newValues)
                                )
                );

                publishUserEvent(userId.toString(), "UserUpdatedByAdmin", Map.of(
                                "userId", userId.toString(),
                                "adminEmail", adminEmail,
                                "oldValues", oldValues,
                                "newValues", newValues
                ));

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
                                List.of(
                                        new AuditLogDetail("message", "Admin suspended user"),
                                        new AuditLogDetail("adminEmail", adminEmail),
                                        new AuditLogDetail("userEmail", user.getEmail()),
                                        new AuditLogDetail("reason", reason)
                                ));

                publishUserEvent(userId.toString(), "UserSuspended", Map.of(
                                "userId", userId.toString(),
                                "adminEmail", adminEmail,
                                "reason", reason
                ));

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
                                List.of(
                                        new AuditLogDetail("message", "Admin activated user"),
                                        new AuditLogDetail("adminEmail", adminEmail),
                                        new AuditLogDetail("userEmail", user.getEmail())
                                ));

                publishUserEvent(userId.toString(), "UserActivated", Map.of(
                                "userId", userId.toString(),
                                "adminEmail", adminEmail
                ));

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
                                List.of(
                                        new AuditLogDetail("message", "Admin changed user status"),
                                        new AuditLogDetail("adminEmail", adminEmail),
                                        new AuditLogDetail("userEmail", user.getEmail()),
                                        new AuditLogDetail("oldStatus", oldStatus),
                                        new AuditLogDetail("newStatus", status),
                                        new AuditLogDetail("oldValues", oldStatus.toString()),
                                        new AuditLogDetail("newValues", status.toString())
                                )
                );

                publishUserEvent(userId.toString(), "UserStatusChanged", Map.of(
                                "userId", userId.toString(),
                                "adminEmail", adminEmail,
                                "oldStatus", oldStatus.toString(),
                                "newStatus", status.toString()
                ));

                log.info("User {} status changed from {} to {} by admin {}",
                                user.getEmail(), oldStatus, status, adminEmail);

                return customerMapper.toDto(user);
        }

        // ══════════════════════════════════════════════════════════
        // MÉTODOS AUXILIARES
        // ══════════════════════════════════════════════════════════
        @Transactional(readOnly = true)
        @Override
        public User getEntityUserByEmail(String email) {
                return userRepository.findActiveByEmail(email)
                                .orElseThrow(() -> new UserNotFoundException(email));
        }
        @Transactional(readOnly = true)
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

        private void publishUserEvent(String aggregateId, String eventType, Map<String, Object> payloadData) {
                try {
                        outboxEventPort.save(new OutboxEvent(
                                "User", aggregateId, eventType,
                                objectMapper.writeValueAsString(payloadData),
                                KafkaTopic.USER_EVENTS
                        ));
                } catch (JsonProcessingException e) {
                        throw new IllegalStateException("Failed to serialize event payload", e);
                }
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
