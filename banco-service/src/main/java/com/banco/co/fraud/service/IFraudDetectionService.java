package com.banco.co.fraud.service;

import com.banco.co.fraud.dto.TransactionFraudContext;
import com.banco.co.fraud.enums.FraudAnalysisResult;

public interface IFraudDetectionService {
    FraudAnalysisResult analyze(TransactionFraudContext context);
}
