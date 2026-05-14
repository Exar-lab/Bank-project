package com.banco.co.envelope.model;

import com.banco.co.envelope.enums.EnvelopeType;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class EnvelopeLifecycleTest {

    @Test
    void testGenerateEnvelopeData_WhenEnvelopeCodeIsNull_FillsEnvelopeCode() {
        int currentYear = LocalDate.now().getYear();
        Envelope envelope = new Envelope();
        envelope.setType(EnvelopeType.SAVINGS);

        envelope.generateEnvelopeData();

        assertThat(envelope.getEnvelopeCode())
                .matches("ENV-" + currentYear + "-[ABCDEFGHJKLMNPQRSTUVWXYZ23456789]{10}");
    }

    @Test
    void testGenerateEnvelopeData_WhenEnvelopeCodeAlreadyExists_PreservesExistingValue() {
        Envelope envelope = new Envelope();
        envelope.setType(EnvelopeType.SAVINGS);
        envelope.setEnvelopeCode("ENV-EXISTING-001");

        envelope.generateEnvelopeData();

        assertThat(envelope.getEnvelopeCode()).isEqualTo("ENV-EXISTING-001");
    }
}
