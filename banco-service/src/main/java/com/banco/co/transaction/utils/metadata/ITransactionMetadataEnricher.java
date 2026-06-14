package com.banco.co.transaction.utils.metadata;

import com.banco.co.transaction.dto.TransactionRequestMetadataDto;
import com.banco.co.transaction.enums.TransactionChannel;
import com.banco.co.transaction.domain.model.Transaction;

public interface ITransactionMetadataEnricher {
    void enrich(Transaction transaction, TransactionRequestMetadataDto metadata, TransactionChannel channel);
}
