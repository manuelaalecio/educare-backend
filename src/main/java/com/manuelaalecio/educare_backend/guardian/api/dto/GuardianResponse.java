package com.manuelaalecio.educare_backend.guardian.api.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * A guardian with every field and the linked children (by name), as returned by the lookup by id and the update.
 * Optional fields not informed are {@code null}; the work fields are {@code null} when {@code notWorking} is
 * {@code true}. Never carries the authors of the record.
 */
public record GuardianResponse(
		UUID id,
		String name,
		@Nullable String maritalStatus,
		@Nullable LocalDate birthDate,
		@Nullable String cpf,
		@Nullable String rg,
		String phone,
		boolean notWorking,
		@Nullable String workplace,
		@Nullable String workAddress,
		@Nullable String workPhone,
		List<GuardianChildResponse> children,
		Instant createdAt,
		Instant updatedAt) {
}
