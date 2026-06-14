package com.banco.co.transaction.mapper;

import com.banco.co.transaction.domain.model.Transaction;
import com.banco.co.transaction.dto.TransactionResponseDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ITransactionMapper {

    // fromAccountCode and toAccountCode are direct fields on the domain model —
    // set by the service (from loaded Account objects) or by the adapter's toDomain() mapper.
    // confirmationMessage has no source on Transaction — left null (not yet computed by service).
    @Mapping(target = "confirmationMessage", ignore = true)
    TransactionResponseDto toDto(Transaction transaction);
}
