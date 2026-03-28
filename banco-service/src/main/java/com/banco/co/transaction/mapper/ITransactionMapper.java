package com.banco.co.transaction.mapper;

import com.banco.co.transaction.dto.TransactionResponseDto;
import com.banco.co.transaction.model.Transaction;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ITransactionMapper {

    @Mapping(source = "fromAccount.accountCode", target = "fromAccountCode")
    @Mapping(source = "toAccount.accountCode", target = "toAccountCode")
    TransactionResponseDto toDto(Transaction transaction);
}
