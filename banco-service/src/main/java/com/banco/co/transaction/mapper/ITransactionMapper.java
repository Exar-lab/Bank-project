package com.banco.co.transaction.mapper;

import com.banco.co.transaction.dto.TransactionResponseDto;
import com.banco.co.transaction.model.Transaction;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ITransactionMapper {
    TransactionResponseDto toDto(Transaction transaction);
}
