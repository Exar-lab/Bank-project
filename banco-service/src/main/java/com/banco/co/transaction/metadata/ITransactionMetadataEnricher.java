package com.banco.co.transaction.metadata;

import com.banco.co.transaction.enums.TransactionChannel;
import com.banco.co.transaction.model.Transaction;
import jakarta.servlet.http.HttpServletRequest;

public interface ITransactionMetadataEnricher {
    void enrich(Transaction transaction, HttpServletRequest request, TransactionChannel channel);

}
