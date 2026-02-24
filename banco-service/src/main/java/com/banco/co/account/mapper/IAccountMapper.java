package com.banco.co.account.mapper;

import com.banco.co.account.dto.AccountRequestDto;
import com.banco.co.account.dto.AccountResponseDto;
import com.banco.co.account.dto.AccountUpdateDto;
import com.banco.co.account.model.Account;
import org.mapstruct.*;

@Mapper(componentModel = "spring")
public interface IAccountMapper {

    // ══════════════════════════════════════════════════════════
    // Entity ↔ DTO Conversions
    // ══════════════════════════════════════════════════════════

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "accountCode", ignore = true)
    @Mapping(target = "accountNumber", ignore = true)
    @Mapping(target = "balance", ignore = true)
    @Mapping(target = "availableBalance", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "lastTransactionAt", ignore = true)
    @Mapping(target = "user", ignore = true)
    @Mapping(target = "bank", ignore = true)
    @Mapping(target = "cards", ignore = true)
    @Mapping(target = "envelopes", ignore = true)
    Account toEntity(AccountRequestDto dto);

    @Mapping(source = "user.id", target = "userId")
    @Mapping(source = "bank.id", target = "bankId")
    AccountResponseDto toDto(Account account);

    // ══════════════════════════════════════════════════════════
    // Partial Update
    // ══════════════════════════════════════════════════════════

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "accountCode", ignore = true)
    @Mapping(target = "accountNumber", ignore = true)
    @Mapping(target = "accountType", ignore = true)
    @Mapping(target = "balance", ignore = true)
    @Mapping(target = "availableBalance", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "lastTransactionAt", ignore = true)
    @Mapping(target = "user", ignore = true)
    @Mapping(target = "bank", ignore = true)
    @Mapping(target = "cards", ignore = true)
    @Mapping(target = "envelopes", ignore = true)
    void updateEntityFromDto(AccountUpdateDto dto, @MappingTarget Account account);

    // ══════════════════════════════════════════════════════════
    // JSON Serialization (for Audit Logs)
    // ══════════════════════════════════════════════════════════

    String toJsonString(Account account);

}
