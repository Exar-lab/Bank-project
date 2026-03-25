package com.banco.co.outbox.repository;

import com.banco.co.outbox.enums.OutboxStatus;
import com.banco.co.outbox.model.OutboxEvent;
import com.banco.co.outbox.port.IOutboxEventPort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface IOutboxEventRepository extends JpaRepository<OutboxEvent, Long>, IOutboxEventPort {

    @Query("SELECT e FROM OutboxEvent e WHERE e.status = :status ORDER BY e.createdAt ASC")
    @Transactional(readOnly = true)
    List<OutboxEvent> findByStatus(@Param("status") OutboxStatus status);
}
