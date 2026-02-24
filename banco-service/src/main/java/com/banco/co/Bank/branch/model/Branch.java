package com.banco.co.Bank.branch.model;

import com.banco.co.Bank.branch.enums.BranchStatus;
import com.banco.co.Bank.branch.enums.BranchType;
import com.banco.co.Bank.branch.generator.BranchCodeGenerator;
import com.banco.co.Bank.model.Bank;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;
@EntityListeners(AuditingEntityListener.class)
@Entity
@Getter
@Setter
@NoArgsConstructor
public class Branch {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // Identificadores
    @Column(unique = true, nullable = false, length = 20)
    private String branchCode;  // BCR-SJ-001

    // Información básica
    @Column(nullable = false, length = 100)
    private String name;  // Sucursal Centro San José

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BranchStatus status = BranchStatus.ACTIVE;

    @Enumerated(EnumType.STRING)
    private BranchType type;  // MAIN, BRANCH, ATM_ONLY, MOBILE

    // Relación con banco
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bank_id", nullable = false)
    private Bank bank;

    // Ubicación detallada
    @Column(nullable = false, length = 200)
    private String address;  // Avenida Central, Calle 0

    @Column(nullable = false, length = 100)
    private String city;  // San José

    @Column(length = 100)
    private String province;  // San José

    @Column(length = 20)
    private String postalCode;

    @Column(nullable = false, length = 100)
    private String country;  // Costa Rica

    // Coordenadas GPS para mapas
    @Column(precision = 10, scale = 7)
    private Double latitude;  // 9.9280694

    @Column(precision = 10, scale = 7)
    private Double longitude;  // -84.0907246

    // Contacto
    @Column(nullable = false, length = 20)
    private String phone;

    @Column(length = 100)
    private String email;

    // Horarios
    @Column(length = 50)
    private String openingTime;  // "08:00"

    @Column(length = 50)
    private String closingTime;  // "16:00"

    @Column(length = 200)
    private String businessHours;  // "Lunes a Viernes: 8:00-16:00, Sábado: 9:00-12:00"

    // Servicios disponibles
    @Column(nullable = false)
    private boolean hasAtm = false;

    @Column(nullable = false)
    private boolean hasVault = false;  // Bóveda

    @Column(nullable = false)
    private boolean hasParking = false;

    @Column(nullable = false)
    private boolean hasAccessibility = false;  // Acceso para discapacitados

    @Column(nullable = false)
    private boolean acceptsDeposits = true;

    @Column(nullable = false)
    private boolean acceptsLoans = false;

    // Capacidad
    @Column
    private Integer atmCount;  // Cantidad de cajeros

    @Column
    private Integer tellerCount;  // Cantidad de ventanillas

    @Column
    private Integer advisorCount;  // Cantidad de ejecutivos

    // Descripción
    @Column(length = 500)
    private String description;  // Información adicional

    @Column(length = 500)
    private String notes;  // Notas internas

    // Auditoría
    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Column
    private LocalDateTime lastInspectionDate;  // Última inspección

    // Generación automática
    @PrePersist
    public void generateBranchCode() {
        if (this.branchCode == null && this.bank != null) {
            this.branchCode = BranchCodeGenerator.generate(
                    this.bank.getAbbreviation(),
                    this.city
            );
        }
    }

    // Métodos de negocio
    public boolean isOpen() {
        return this.status == BranchStatus.ACTIVE;
    }

    public boolean isOpen(LocalTime currentTime, DayOfWeek dayOfWeek) {
        if (!isOpen()) return false;

        // Lógica para verificar si está abierta según horario
        // Simplificado, en producción sería más complejo
        return true;
    }



}
