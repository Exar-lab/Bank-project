package com.banco.co.security.securityhasher;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public class HashUtils {
    private static final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(12);

    // ── BCrypt ──────────────────────────────────────────────
    public static String hashBcrypt(String pin) {
        return encoder.encode(pin);
    }

    public static boolean verifyBcrypt(String pin, String storedHash) {
        return encoder.matches(pin, storedHash);
    }

    // ── SHA-256 ─────────────────────────────────────────────
    public static String hashSha256(String pin) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(pin.getBytes());
            return HexFormat.of().formatHex(hashBytes); // Java 17+
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 no disponible", e);
        }
    }

    public static boolean verifySha256(String pin, String storedHash) {
        return hashSha256(pin).equals(storedHash);
    }
}
