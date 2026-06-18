package com.banco.co.user.model;

import com.banco.co.account.adapter.out.jpa.AccountEntity;
import com.banco.co.auditLog.model.AuditLog;
import com.banco.co.user.generator.UserCodeGenerator;
import com.banco.co.user.enums.DocumentType;
import com.banco.co.user.enums.KycStatus;
import com.banco.co.user.enums.UserStatus;
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

@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(unique = true,nullable = false,length = 20)
    private String userCode;

    @Column(nullable = false,length = 50)
    private String fistName;

    @Column(nullable = false,length = 50)
    private String lastName;

    @Column(length = 20)
    private String username;

    @Column(unique = true,nullable = false,length = 100)
    private String email;

    @Column(unique = true,nullable = false,length = 12)
    private String documentNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DocumentType documentType;

    @Column(nullable = false)
    private LocalDate birthDate;

    @Column(unique = true,nullable = false,length = 20)
    private String phoneNumber;

    @Enumerated(EnumType.STRING)
    private UserStatus status =  UserStatus.ACTIVE;

    @Enumerated(EnumType.STRING)
    private KycStatus kycStatus = KycStatus.PENDING;

    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private UserCredential credential; // Credenciales

    @CreatedDate
    @Column(nullable = false,updatable = false)
    private LocalDateTime createdDate;

    @LastModifiedDate
    private LocalDateTime updatedDate;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<AuditLog>  auditLogs = new ArrayList<>();

    @Column(nullable = false,length = 200)
    private String address;

    @OneToMany(mappedBy = "user",cascade = CascadeType.ALL, orphanRemoval = true)
    @Column(name = "accountId")
    private List<AccountEntity> accounts;


    @PrePersist
    public void generateData(){
        if (this.userCode == null){
            this.userCode = UserCodeGenerator.generate();
        }
        if(this.username == null){
            this.username = UserCodeGenerator.generateUsername(this.fistName);
        }
    }
}
