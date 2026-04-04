package com.banco.co.fraud.service;

import com.banco.co.fraud.config.FraudDetectionProperties;
import com.banco.co.fraud.dto.TransactionFraudContext;
import com.banco.co.fraud.enums.FraudAnalysisResult;
import com.banco.co.fraud.riskprofile.enums.RiskProfileFallbackPolicy;
import com.banco.co.fraud.riskprofile.service.IRiskProfileGateService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(
        classes = {
                FraudDetectionServiceImpl.class,
                FraudDetectionServiceImplWiringContextTest.TestConfig.class
        },
        properties = "spring.task.scheduling.enabled=false"
)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class FraudDetectionServiceImplWiringContextTest {

    private final FraudDetectionServiceImpl fraudDetectionService;

    @MockitoBean
    private IRiskProfileGateService riskProfileGateService;

    FraudDetectionServiceImplWiringContextTest(FraudDetectionServiceImpl fraudDetectionService) {
        this.fraudDetectionService = fraudDetectionService;
    }

    @Test
    void testContextLoads_WhenFraudDetectionServiceDependsOnInterface_CreatesBeanSuccessfully() {
        assertNotNull(fraudDetectionService);

        TransactionFraudContext context = new TransactionFraudContext(
                "tx-wiring-001", "ACC-001", "ACC-002",
                new BigDecimal("1"), "CRC",
                "TXN-WIRING-001", "TRANSFER", "WEB",
                null, null, null, null, null
        );

        when(riskProfileGateService.evaluate(context)).thenReturn(FraudAnalysisResult.CLEAR);

        FraudAnalysisResult result = fraudDetectionService.analyze(context);

        assertEquals(FraudAnalysisResult.CLEAR, result);
        verify(riskProfileGateService).evaluate(context);
    }

    @Configuration(proxyBeanMethods = false)
    static class TestConfig {
        @Bean
        FraudDetectionProperties fraudDetectionProperties() {
            return new FraudDetectionProperties(
                    true,
                    new BigDecimal("10000000"),
                    new BigDecimal("50000000"),
                    25,
                    RiskProfileFallbackPolicy.FAIL_OPEN_LEGACY_THRESHOLD
            );
        }
    }
}
