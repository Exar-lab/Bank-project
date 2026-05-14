package com.banco.co.user.domain.model;

import com.banco.co.user.enums.DocumentType;
import com.banco.co.user.enums.KycStatus;
import com.banco.co.user.enums.UserStatus;
import com.banco.co.user.generator.UserCodeGenerator;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Pure domain model — ZERO JPA, ZERO Spring imports.
 * Cross-feature references use UUID only (roleId instead of Role entity).
 * Coexists with com.banco.co.user.model.User during the additive migration phase.
 */
public class User {

    private UUID id;

    private String userCode;

    private String fistName;

    private String lastName;

    private String username;

    private String email;

    private String documentNumber;

    private DocumentType documentType;

    private LocalDate birthDate;

    private String phoneNumber;

    private UserStatus status = UserStatus.ACTIVE;

    private KycStatus kycStatus = KycStatus.PENDING;

    private String address;

    /**
     * Cross-feature reference: replaced @ManyToOne Role with UUID.
     * The adapter layer resolves the actual Role entity when needed.
     */
    private UUID roleId;

    private LocalDateTime createdDate;

    private LocalDateTime updatedDate;

    public User() {
    }

    // ══════════════════════════════════════════════════════════
    // Domain behaviour (mirrors @PrePersist logic from legacy entity)
    // ══════════════════════════════════════════════════════════

    public void generateData() {
        if (this.userCode == null) {
            this.userCode = UserCodeGenerator.generate();
        }
        if (this.username == null) {
            this.username = UserCodeGenerator.generateUsername(this.fistName);
        }
    }

    // ══════════════════════════════════════════════════════════
    // Getters and Setters
    // ══════════════════════════════════════════════════════════

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getUserCode() {
        return userCode;
    }

    public void setUserCode(String userCode) {
        this.userCode = userCode;
    }

    public String getFistName() {
        return fistName;
    }

    public void setFistName(String fistName) {
        this.fistName = fistName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getDocumentNumber() {
        return documentNumber;
    }

    public void setDocumentNumber(String documentNumber) {
        this.documentNumber = documentNumber;
    }

    public DocumentType getDocumentType() {
        return documentType;
    }

    public void setDocumentType(DocumentType documentType) {
        this.documentType = documentType;
    }

    public LocalDate getBirthDate() {
        return birthDate;
    }

    public void setBirthDate(LocalDate birthDate) {
        this.birthDate = birthDate;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public UserStatus getStatus() {
        return status;
    }

    public void setStatus(UserStatus status) {
        this.status = status;
    }

    public KycStatus getKycStatus() {
        return kycStatus;
    }

    public void setKycStatus(KycStatus kycStatus) {
        this.kycStatus = kycStatus;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public UUID getRoleId() {
        return roleId;
    }

    public void setRoleId(UUID roleId) {
        this.roleId = roleId;
    }

    public LocalDateTime getCreatedDate() {
        return createdDate;
    }

    public void setCreatedDate(LocalDateTime createdDate) {
        this.createdDate = createdDate;
    }

    public LocalDateTime getUpdatedDate() {
        return updatedDate;
    }

    public void setUpdatedDate(LocalDateTime updatedDate) {
        this.updatedDate = updatedDate;
    }
}
