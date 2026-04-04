package com.banco.co.fraud.service;

import com.banco.co.fraud.config.FraudDetectionProperties;
import com.banco.co.fraud.dto.TransactionFraudContext;
import com.banco.co.fraud.enums.FraudAnalysisResult;
import com.banco.co.fraud.riskprofile.service.IRiskProfileGateService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class FraudDetectionServiceImpl implements IFraudDetectionService {

    private static final Logger log = LoggerFactory.getLogger(FraudDetectionServiceImpl.class);

    private final FraudDetectionProperties properties;
    private final IRiskProfileGateService riskProfileGateService;

    public FraudDetectionServiceImpl(
            FraudDetectionProperties properties,
            IRiskProfileGateService riskProfileGateService
    ) {
        this.properties = properties;
        this.riskProfileGateService = riskProfileGateService;
    }

    @Override
    public FraudAnalysisResult analyze(TransactionFraudContext context) {
        FraudAnalysisResult result = properties.riskProfileEnabled()
                ? riskProfileGateService.evaluate(context)
                : computeResult(context);
        log.info("Fraud analysis: transactionId={}, amount={}, result={}",
                context.transactionId(), context.amount(), result);
        return result;
    }

    private FraudAnalysisResult computeResult(TransactionFraudContext context) {
        if (context.amount().compareTo(properties.blockedThreshold()) >= 0) {
            return FraudAnalysisResult.BLOCKED;
        }
        if (context.amount().compareTo(properties.suspiciousThreshold()) >= 0) {
            return FraudAnalysisResult.SUSPICIOUS;
        }
        return FraudAnalysisResult.CLEAR;
    }
}
