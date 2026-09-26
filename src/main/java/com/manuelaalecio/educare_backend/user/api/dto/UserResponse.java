package com.manuelaalecio.educare_backend.user.api.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * A user as returned by the API. Never carries the password nor its hash.
 */
public record UserResponse(UUID id, String name, String login, String role, Instant createdAt, Instant updatedAt) {
}
