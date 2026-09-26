package com.manuelaalecio.educare_backend.user.application;

import java.time.Instant;
import java.util.UUID;

/**
 * A user as exposed to other modules, which cannot see the {@code User} entity: no password nor hash.
 *
 * @param role the role name (e.g. {@code ADMIN})
 */
public record UserAccount(UUID id, String name, String login, String role, Instant createdAt, Instant updatedAt) {
}
