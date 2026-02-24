package com.banco.co.role.model;

import com.banco.co.permission.model.Permission;
import com.banco.co.role.enums.SystemRole;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
@Entity
@Table(name = "roles")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class Role {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(unique = true, nullable = false, length = 100)
    private SystemRole name;  // Enum, no String libre

    @Column(nullable = false, length = 200)
    private String description;

    @Column(nullable = false)
    private int privilegeLevel;  // Nivel jerárquico

    @Column(nullable = false)
    private boolean active = true;

    @Column(nullable = false)
    private boolean systemDefined = true;  // No se puede eliminar si es true

    @ManyToMany(fetch = FetchType.LAZY)  // LAZY, no EAGER
    @JoinTable(
            name = "role_permissions",
            joinColumns = @JoinColumn(name = "role_id"),
            inverseJoinColumns = @JoinColumn(name = "permission_id")
    )
    private Set<Permission> permissions = new HashSet<>();  // Set, no List

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Column(length = 100)
    private String lastModifiedBy;
}
