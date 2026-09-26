package com.manuelaalecio.educare_backend.auth.api.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * The authenticated user's own data. Never carries the password nor its hash.
 */
public record MeResponse(UUID id, String name, String login, String role, Instant createdAt, Instant updatedAt) {
}
