package com.banco.co.user.mapper.costumer;

import com.banco.co.user.dto.customer.CustomerRequestDto;
import com.banco.co.user.dto.customer.CustomerResponseDto;
import com.banco.co.user.dto.customer.CustomerUpdateDto;
import com.banco.co.user.model.User;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.mapstruct.*;

@Mapper(componentModel = "spring")
public interface ICustomerMapper {

    // ══════════════════════════════════════════════════════════
    // Entity ↔ DTO Conversions
    // ══════════════════════════════════════════════════════════

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "userCode", ignore = true)
    @Mapping(target = "username", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "kycStatus", ignore = true)
    @Mapping(target = "credential", ignore = true)
    @Mapping(target = "createdDate", ignore = true)
    @Mapping(target = "updatedDate", ignore = true)
    @Mapping(target = "auditLogs", ignore = true)
    @Mapping(target = "accounts", ignore = true)
    User toEntity(CustomerRequestDto dto);

    CustomerResponseDto toDto(User user);

    // ══════════════════════════════════════════════════════════
    // Partial Update
    // ══════════════════════════════════════════════════════════

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "userCode", ignore = true)
    @Mapping(target = "username", ignore = true)
    @Mapping(target = "email", ignore = true)
    @Mapping(target = "documentNumber", ignore = true)
    @Mapping(target = "documentType", ignore = true)
    @Mapping(target = "birthDate", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "kycStatus", ignore = true)
    @Mapping(target = "credential", ignore = true)
    @Mapping(target = "createdDate", ignore = true)
    @Mapping(target = "updatedDate", ignore = true)
    @Mapping(target = "auditLogs", ignore = true)
    @Mapping(target = "accounts", ignore = true)
    void updateEntityFromDto(CustomerUpdateDto dto, @MappingTarget User user);

    // ══════════════════════════════════════════════════════════
    // JSON Serialization (for Audit Logs)
    // ══════════════════════════════════════════════════════════

    String toJsonString(User user);
}
