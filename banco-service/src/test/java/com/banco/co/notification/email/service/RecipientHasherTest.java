package com.banco.co.notification.email.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RecipientHasherTest {

    @Test
    void testHash_WhenEmailCaseDiffers_ThenReturnsSameDigest() {
        RecipientHasher hasher = new RecipientHasher();

        String hash1 = hasher.hash("USER@BANCO.CO");
        String hash2 = hasher.hash("user@banco.co");

        assertThat(hash1).isEqualTo(hash2);
        assertThat(hash1).hasSize(64);
    }
}
