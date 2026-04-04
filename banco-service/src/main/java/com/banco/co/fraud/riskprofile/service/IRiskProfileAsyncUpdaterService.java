package com.banco.co.fraud.riskprofile.service;

import com.banco.co.fraud.riskprofile.dto.TransactionCompletedRiskEvent;

public interface IRiskProfileAsyncUpdaterService {

    boolean updateFromTransactionCompleted(TransactionCompletedRiskEvent event);
}
