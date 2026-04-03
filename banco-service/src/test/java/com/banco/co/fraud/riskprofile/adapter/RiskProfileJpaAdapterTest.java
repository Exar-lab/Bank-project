package com.banco.co.fraud.riskprofile.adapter;

import com.banco.co.fraud.riskprofile.repository.IRiskProfileEventProcessingRepository;
import com.banco.co.fraud.riskprofile.repository.IRiskProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RiskProfileJpaAdapterTest {

    @Mock
    private IRiskProfileRepository riskProfileRepository;

    @Mock
    private IRiskProfileEventProcessingRepository eventProcessingRepository;

    private RiskProfileJpaAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new RiskProfileJpaAdapter(riskProfileRepository, eventProcessingRepository);
    }

    @Test
    void testMarkEventProcessed_NewEvent_ReturnsTrue() {
        when(eventProcessingRepository.saveAndFlush(org.mockito.ArgumentMatchers.any()))
                .thenReturn(null);

        boolean result = adapter.markEventProcessed("evt-1", "risk-profile-consumer");

        assertTrue(result);
        verify(eventProcessingRepository).saveAndFlush(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void testMarkEventProcessed_DuplicatedEvent_ReturnsFalse() {
        when(eventProcessingRepository.saveAndFlush(org.mockito.ArgumentMatchers.any()))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));

        boolean result = adapter.markEventProcessed("evt-1", "risk-profile-consumer");

        assertFalse(result);
    }
}
