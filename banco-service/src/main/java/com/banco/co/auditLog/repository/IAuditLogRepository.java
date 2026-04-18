package com.banco.co.auditLog.repository;

import com.banco.co.auditLog.model.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
@Repository
public interface IAuditLogRepository extends JpaRepository<AuditLog, UUID> {
    List<AuditLog> findByEmail(String email);

    @Query("SELECT a FROM AuditLog a LEFT JOIN FETCH a.details")
    List<AuditLog> findAllWithDetails();
}
