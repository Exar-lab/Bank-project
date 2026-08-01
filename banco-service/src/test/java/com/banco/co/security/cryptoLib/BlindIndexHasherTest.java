package com.banco.co.security.cryptoLib;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BlindIndexHasherTest {

    @Test
    void testSha256Hex_SameInputTwice_ProducesSameHash() {
        String hashOne = BlindIndexHasher.sha256Hex("4111111111111111");
        String hashTwo = BlindIndexHasher.sha256Hex("4111111111111111");

        assertThat(hashOne).isEqualTo(hashTwo);
    }

    @Test
    void testSha256Hex_DifferentInputs_ProducesDifferentHashes() {
        String hashOne = BlindIndexHasher.sha256Hex("4111111111111111");
        String hashTwo = BlindIndexHasher.sha256Hex("4111111111111112");

        assertThat(hashOne).isNotEqualTo(hashTwo);
    }

    @Test
    void testSha256Hex_ValidInput_Returns64CharHexString() {
        String hash = BlindIndexHasher.sha256Hex("4111111111111111");

        assertThat(hash).hasSize(64).matches("[0-9a-f]+");
    }
}
