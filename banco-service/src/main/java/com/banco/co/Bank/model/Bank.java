package com.banco.co.Bank.model;

import com.banco.co.Bank.branch.enums.BranchStatus;
import com.banco.co.Bank.branch.model.Branch;
import com.banco.co.Bank.codeResolver.CountryCodeResolver;
import com.banco.co.Bank.enums.BankStatus;
import com.banco.co.Bank.enums.BankType;
import com.banco.co.security.codeGenerator.CodeGenerator;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
@EntityListeners(AuditingEntityListener.class)
@Table(name = "banks", indexes = {
        @Index(name = "idx_bank_code", columnList = "bankCode"),
        @Index(name = "idx_swift_code", columnList = "swiftCode")
})
@Entity
@Getter
@Setter
@NoArgsConstructor
public class Bank {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // Identificadores
    @Column(unique = true, nullable = false, length = 20)
    private String bankCode;  // Auto-generado: BCR-2024-X7K9P2

    @Column(unique = true, nullable = false, length = 11)
    private String swiftCode;  // BACCCRSJXXX (código internacional)

    @Column(unique = true, length = 20)
    private String routingNumber;  // Para transferencias interbancarias

    // Información básica
    @Column(unique = true, nullable = false, length = 100)
    private String name;  // Banco de Costa Rica

    @Column(unique = true, nullable = false, length = 10)
    private String abbreviation;  // BCR

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BankType bankType;  // PUBLIC, PRIVATE, CREDIT_UNION, ONLINE

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BankStatus status = BankStatus.ACTIVE;

    // Ubicación principal (casa matriz)
    @Column(nullable = false, length = 100)
    private String country;  // Costa Rica

    @Column(length = 3, nullable = false)
    private String countryCode;  // CRC (ISO 3166-1)

    @Column(nullable = false, length = 100)
    private String city;  // San José

    @Column(nullable = false, length = 200)
    private String address;  // Dirección completa de casa matriz

    @Column(length = 20)
    private String postalCode;

    // Contacto
    @Column(nullable = false, length = 20)
    private String phone;  // +506 2287-9000

    @Column(length = 20)
    private String fax;

    @Column(nullable = false, length = 100)
    private String email;  // info@bancobcr.com

    @Column(length = 200)
    private String website;  // https://www.bancobcr.com

    // Legal
    @Column(unique = true, nullable = false, length = 50)
    private String taxId;  // RUC, EIN, NIT según país

    @Column(length = 100)
    private String registrationNumber;  // Número de registro ante superintendencia

    @Column(nullable = false, length = 100)
    private LocalDate foundedDate;  // Fecha de fundación

    // Visuales
    @Column(length = 500)
    private String logoUrl;  // URL del logo

    @Column(length = 7)
    private String primaryColor;  // #0066CC (para branding en apps)

    // Relaciones
    @OneToMany(mappedBy = "bank", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Branch> branches = new ArrayList<>();

    // Auditoría
    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    // Generación automática
    @PrePersist
    public void generateBankCode() {
        if (this.bankCode == null) {
            this.bankCode = CodeGenerator.generateWithPrefix(
                    this.abbreviation,
                    12
            );
        }
        if (this.countryCode == null && this.country != null) {
            this.countryCode = CountryCodeResolver.resolve(this.country);
        }
    }

    // Métodos de negocio
    public void addBranch(Branch branch) {
        branches.add(branch);
        branch.setBank(this);
    }

    public void removeBranch(Branch branch) {
        branches.remove(branch);
        branch.setBank(null);
    }

    public long getActiveBranchCount() {
        return branches.stream()
                .filter(b -> b.getStatus() == BranchStatus.ACTIVE)
                .count();
    }

    public boolean isOperational() {
        return this.status == BankStatus.ACTIVE
                && this.getActiveBranchCount() > 0;
    }
}
