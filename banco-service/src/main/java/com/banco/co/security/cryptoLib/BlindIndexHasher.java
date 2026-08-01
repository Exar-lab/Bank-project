package com.banco.co.security.cryptoLib;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Deterministic SHA-256 hash for values stored with random-IV encryption
 * (see JasyptEncryptor) that still need a DB-enforceable uniqueness check —
 * a "blind index". The encrypted column itself can no longer carry a unique
 * constraint once encryption is non-deterministic, since the same plaintext
 * produces different ciphertext on every write.
 */
public final class BlindIndexHasher {

    private BlindIndexHasher() {
    }

    public static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 algorithm is not available", ex);
        }
    }
}
