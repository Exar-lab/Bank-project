package com.banco.co.fraud.riskprofile.service;

import com.banco.co.fraud.dto.TransactionFraudContext;
import com.banco.co.fraud.enums.FraudAnalysisResult;
import com.banco.co.fraud.riskprofile.dto.TransactionCompletedRiskEvent;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RiskProfileServiceInterfacesContractTest {

    @Test
    void testIRiskProfileGateService_DeclaresEvaluateContract() throws Exception {
        Method evaluate = IRiskProfileGateService.class.getMethod("evaluate", TransactionFraudContext.class);

        assertEquals(FraudAnalysisResult.class, evaluate.getReturnType());
    }

    @Test
    void testIRiskProfileAsyncUpdaterService_DeclaresUpdateContract() throws Exception {
        Method update = IRiskProfileAsyncUpdaterService.class
                .getMethod("updateFromTransactionCompleted", TransactionCompletedRiskEvent.class);

        assertEquals(boolean.class, update.getReturnType());
    }
}
