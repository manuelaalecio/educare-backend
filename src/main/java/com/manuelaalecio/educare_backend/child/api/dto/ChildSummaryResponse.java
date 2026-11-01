package com.manuelaalecio.educare_backend.child.api.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A child as listed: only what the list needs, without documents, address nor health data (data minimization,
 * LGPD). Never carries the authors of the record.
 */
public record ChildSummaryResponse(UUID id, String name, LocalDate birthDate, Instant createdAt, Instant updatedAt) {
}
