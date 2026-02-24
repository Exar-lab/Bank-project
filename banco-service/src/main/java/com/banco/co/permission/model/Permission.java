package com.banco.co.permission.model;

import com.banco.co.permission.enums.SystemPermission;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "permissions")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class Permission {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(unique = true, nullable = false, length = 100)
    private SystemPermission name;  // Enum, no String libre

    @Column(nullable = false, length = 50)
    private String scope;  // "account:read", "transaction:create"

    @Column(nullable = false, length = 50)
    private String resource;  // "account", "transaction", "card"

    @Column(nullable = false, length = 50)
    private String action;  // "read", "create", "update", "delete"

    @Column(nullable = false, length = 200)
    private String description;

    @Column(nullable = false)
    private boolean active = true;

    @Column(nullable = false)
    private boolean systemDefined = true;  // Si es true, NO se puede eliminar

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

}