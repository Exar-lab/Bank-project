package com.banco.co.user.domain.model;

/**
 * Immutable projection of a User consumed by other features.
 * Replaces cross-feature @ManyToOne relationships with a value object.
 */
public record UserSnapshot(
        String userId,
        String email,
        String username,
        String role
) {
}
