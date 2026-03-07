package com.banco.co.envelope.mapper;

import com.banco.co.envelope.dto.EnvelopeRequestDto;
import com.banco.co.envelope.dto.EnvelopeResponseDto;
import com.banco.co.envelope.dto.EnvelopeUpdateDto;
import com.banco.co.envelope.model.Envelope;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.lang.annotation.Target;

@Mapper(componentModel = "spring")
public interface IEnvelopeMapper {
    EnvelopeResponseDto toDto(Envelope envelope);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "envelopeCode", ignore = true) // Se genera en @PrePersist
    @Mapping(target = "balance", ignore = true) // Solo se altera mediante depósitos/retiros
    @Mapping(target = "progressPercentage", ignore = true) // Calculado por lógica interna
    @Mapping(target = "totalDeposits", ignore = true)
    @Mapping(target = "totalWithdrawals", ignore = true)
    @Mapping(target = "totalDepositedAmount", ignore = true)
    @Mapping(target = "totalWithdrawnAmount", ignore = true)
    @Mapping(target = "createdAt", ignore = true) // Manejado por @CreatedDate
    @Mapping(target = "updatedAt", ignore = true) // Manejado por @LastModifiedDate
    @Mapping(target = "completedAt", ignore = true)
    @Mapping(target = "lastTransactionAt", ignore = true)
    @Mapping(target = "lastTransactionAmount", ignore = true)
    @Mapping(target = "lastTransactionType", ignore = true)
    @Mapping(target = "account", ignore = true) // Se suele asignar manualmente en el Service
    Envelope toEntity(EnvelopeRequestDto dto);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "envelopeCode", ignore = true) // Se genera en @PrePersist
    @Mapping(target = "balance", ignore = true) // Solo se altera mediante depósitos/retiros
    @Mapping(target = "progressPercentage", ignore = true) // Calculado por lógica interna
    @Mapping(target = "totalDeposits", ignore = true)
    @Mapping(target = "totalWithdrawals", ignore = true)
    @Mapping(target = "totalDepositedAmount", ignore = true)
    @Mapping(target = "totalWithdrawnAmount", ignore = true)
    @Mapping(target = "createdAt", ignore = true) // Manejado por @CreatedDate
    @Mapping(target = "updatedAt", ignore = true) // Manejado por @LastModifiedDate
    @Mapping(target = "completedAt", ignore = true)
    @Mapping(target = "lastTransactionAt", ignore = true)
    @Mapping(target = "lastTransactionAmount", ignore = true)
    @Mapping(target = "lastTransactionType", ignore = true)
    @Mapping(target = "account", ignore = true) // Se suele asignar manualmente en el Service
    void updateEntityFromDto(EnvelopeUpdateDto dto, @MappingTarget Envelope envelope);

    String toJsonString(Envelope envelope);
}
