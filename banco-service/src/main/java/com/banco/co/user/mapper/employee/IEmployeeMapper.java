package com.banco.co.user.mapper.employee;

import com.banco.co.user.dto.employee.EmployeeRequestDto;
import com.banco.co.user.dto.employee.EmployeeResponseDto;
import com.banco.co.user.dto.employee.EmployeeUpdateDto;
import com.banco.co.user.model.User;
import tools.jackson.databind.ObjectMapper;
import org.mapstruct.*;

@Mapper(componentModel = "spring")
public interface IEmployeeMapper {

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
    @Mapping(target = "email", expression = "java(dto.email().toLowerCase())")
    User toEntity(EmployeeRequestDto dto);

    EmployeeResponseDto toDto(User user);

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
    void updateEntityFromDto(EmployeeUpdateDto dto, @MappingTarget User user);

    // ══════════════════════════════════════════════════════════
    // JSON Serialization (for Audit Logs)
    // ══════════════════════════════════════════════════════════

     String toJsonString(User user);
}
