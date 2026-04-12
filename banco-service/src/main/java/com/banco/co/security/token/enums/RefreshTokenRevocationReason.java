package com.banco.co.security.token.enums;

public enum RefreshTokenRevocationReason {
    LOGOUT,
    ROTATED,
    REUSE_DETECTED,
    EXPIRED,
    ADMIN_REVOKED
}
