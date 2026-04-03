package com.banco.co.fraud.riskprofile.service;

import com.banco.co.fraud.riskprofile.config.RiskProfileAsyncUpdaterProperties;
import com.banco.co.fraud.riskprofile.dto.RiskProfileUpdateCommand;
import com.banco.co.fraud.riskprofile.dto.TransactionCompletedRiskEvent;
import com.banco.co.fraud.riskprofile.enums.RiskTier;
import com.banco.co.fraud.riskprofile.port.IRiskProfileUpdatePort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RiskProfileAsyncUpdaterServiceTest {

    private static final RiskProfileAsyncUpdaterProperties DEFAULT_PROPERTIES =
            new RiskProfileAsyncUpdaterProperties(
                    "risk-profile-consumer",
                    new BigDecimal("10000000"),
                    new BigDecimal("50000000"),
                    new BigDecimal("100000000"),
                    new BigDecimal("25.00"),
                    new BigDecimal("60.00"),
                    new BigDecimal("85.00"),
                    new BigDecimal("95.00"),
                    "v1"
            );

    @Mock
    private IRiskProfileUpdatePort riskProfileUpdatePort;

    private RiskProfileAsyncUpdaterService service;

    @BeforeEach
    void setUp() {
        service = new RiskProfileAsyncUpdaterService(riskProfileUpdatePort, DEFAULT_PROPERTIES);
    }

    @Test
    void testUpdateFromTransactionCompleted_NewEvent_UpsertsProfile() {
        TransactionCompletedRiskEvent event = new TransactionCompletedRiskEvent(
                "evt-001",
                "tx-001",
                "ACC-001",
                new BigDecimal("60000000"),
                "COMPLETED"
        );
        when(riskProfileUpdatePort.markEventProcessed("evt-001", "risk-profile-consumer"))
                .thenReturn(true);

        boolean processed = service.updateFromTransactionCompleted(event);

        assertTrue(processed);
        ArgumentCaptor<RiskProfileUpdateCommand> commandCaptor = ArgumentCaptor.forClass(RiskProfileUpdateCommand.class);
        verify(riskProfileUpdatePort).upsertFromEvent(commandCaptor.capture());
        assertEquals("evt-001", commandCaptor.getValue().eventId());
        assertEquals("ACC-001", commandCaptor.getValue().accountCode());
        assertEquals(RiskTier.HIGH, commandCaptor.getValue().riskTier());
        assertEquals(new BigDecimal("85.00"), commandCaptor.getValue().dynamicScore());
        assertEquals("v1", commandCaptor.getValue().ruleSetVersion());
    }

    @Test
    void testUpdateFromTransactionCompleted_DuplicatedEvent_SkipsUpsert() {
        TransactionCompletedRiskEvent event = new TransactionCompletedRiskEvent(
                "evt-dup",
                "tx-dup",
                "ACC-002",
                new BigDecimal("1000"),
                "COMPLETED"
        );
        when(riskProfileUpdatePort.markEventProcessed("evt-dup", "risk-profile-consumer"))
                .thenReturn(false);

        boolean processed = service.updateFromTransactionCompleted(event);

        assertFalse(processed);
        verify(riskProfileUpdatePort, never()).upsertFromEvent(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void testUpdateFromTransactionCompleted_UsesConfiguredRuleSetVersionAndThresholds() {
        RiskProfileAsyncUpdaterProperties customProperties = new RiskProfileAsyncUpdaterProperties(
                "risk-profile-consumer",
                new BigDecimal("100"),
                new BigDecimal("200"),
                new BigDecimal("500"),
                new BigDecimal("10.00"),
                new BigDecimal("40.00"),
                new BigDecimal("70.00"),
                new BigDecimal("99.00"),
                "v2"
        );
        service = new RiskProfileAsyncUpdaterService(riskProfileUpdatePort, customProperties);

        TransactionCompletedRiskEvent event = new TransactionCompletedRiskEvent(
                "evt-custom",
                "tx-custom",
                "ACC-003",
                new BigDecimal("600"),
                "COMPLETED"
        );
        when(riskProfileUpdatePort.markEventProcessed("evt-custom", "risk-profile-consumer"))
                .thenReturn(true);

        boolean processed = service.updateFromTransactionCompleted(event);

        assertTrue(processed);
        ArgumentCaptor<RiskProfileUpdateCommand> commandCaptor = ArgumentCaptor.forClass(RiskProfileUpdateCommand.class);
        verify(riskProfileUpdatePort).upsertFromEvent(commandCaptor.capture());
        assertEquals(RiskTier.RESTRICTED, commandCaptor.getValue().riskTier());
        assertEquals(new BigDecimal("99.00"), commandCaptor.getValue().dynamicScore());
        assertEquals("v2", commandCaptor.getValue().ruleSetVersion());
    }
}
