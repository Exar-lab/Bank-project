package com.banco.co.security.cryptoLib;

import org.jasypt.encryption.pbe.StandardPBEStringEncryptor;
import org.jasypt.encryption.pbe.config.SimpleStringPBEConfig;
import org.jasypt.iv.RandomIvGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JasyptEncryptorTest {

    private JasyptEncryptor jasyptEncryptor;

    @BeforeEach
    void setUp() {
        // Mirrors application.yml's jasypt.encryptor.* configuration exactly,
        // so this test exercises the real algorithm/IV pairing used in production.
        SimpleStringPBEConfig config = new SimpleStringPBEConfig();
        config.setPassword("test-jasypt-password");
        config.setAlgorithm("PBEWITHHMACSHA512ANDAES_256");
        config.setIvGenerator(new RandomIvGenerator());

        StandardPBEStringEncryptor stringEncryptor = new StandardPBEStringEncryptor();
        stringEncryptor.setConfig(config);

        jasyptEncryptor = new JasyptEncryptor(stringEncryptor);
    }

    @Test
    void testConvertToDatabaseColumn_ValidValue_ReturnsCiphertextDecryptableBackToOriginal() {
        String plainCardNumber = "4111111111111111";

        String encrypted = jasyptEncryptor.convertToDatabaseColumn(plainCardNumber);
        String decrypted = jasyptEncryptor.convertToEntityAttribute(encrypted);

        assertThat(encrypted).isNotEqualTo(plainCardNumber);
        assertThat(decrypted).isEqualTo(plainCardNumber);
    }

    @Test
    void testConvertToDatabaseColumn_SameValueEncryptedTwice_ProducesDifferentCiphertext() {
        String plainCardNumber = "4111111111111111";

        String firstCiphertext = jasyptEncryptor.convertToDatabaseColumn(plainCardNumber);
        String secondCiphertext = jasyptEncryptor.convertToDatabaseColumn(plainCardNumber);

        // With a random IV generator, encrypting the same plaintext twice must not be
        // deterministic — this is the exact weakness NoIvGenerator + PBEWithMD5AndDES had.
        assertThat(firstCiphertext).isNotEqualTo(secondCiphertext);
    }

    @Test
    void testConvertToDatabaseColumn_NullValue_ReturnsNull() {
        assertThat(jasyptEncryptor.convertToDatabaseColumn(null)).isNull();
    }

    @Test
    void testConvertToEntityAttribute_NullValue_ReturnsNull() {
        assertThat(jasyptEncryptor.convertToEntityAttribute(null)).isNull();
    }
}
