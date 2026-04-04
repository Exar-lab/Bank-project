package com.banco.co.fraud.riskprofile.service;

import com.banco.co.fraud.dto.TransactionFraudContext;
import com.banco.co.fraud.enums.FraudAnalysisResult;

public interface IRiskProfileGateService {

    FraudAnalysisResult evaluate(TransactionFraudContext context);
}
