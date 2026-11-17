package com.manuelaalecio.educare_backend.guardian.api.dto;

import java.util.UUID;

/**
 * A child linked to the guardian: id, current name and the relationship of that link.
 */
public record GuardianChildResponse(UUID id, String name, String relationship) {
}
