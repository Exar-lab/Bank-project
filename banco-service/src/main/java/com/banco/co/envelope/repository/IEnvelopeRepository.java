package com.banco.co.envelope.repository;

import com.banco.co.envelope.model.Envelope;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;
@Repository
public interface IEnvelopeRepository extends JpaRepository<Envelope, UUID> {
}
