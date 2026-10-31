package com.manuelaalecio.educare_backend.classroom.api.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * A class as returned by the API, with its student counts. Never carries the authors of the record.
 */
public record ClassroomResponse(UUID id, String name, long studentCount, long maleCount, long femaleCount,
		Instant createdAt, Instant updatedAt) {
}
