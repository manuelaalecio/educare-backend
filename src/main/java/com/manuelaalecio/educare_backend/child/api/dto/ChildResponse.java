package com.manuelaalecio.educare_backend.child.api.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * A child with every field and its current class, as returned by the creation, the update and the lookup by id.
 * Optional fields not informed are {@code null}. Never carries the authors of the record.
 */
public record ChildResponse(
		UUID id,
		String name,
		LocalDate birthDate,
		ClassroomRefResponse classroom,
		String cep,
		String address,
		String neighborhood,
		String addressNumber,
		@Nullable String addressComplement,
		String city,
		String state,
		@Nullable String birthplace,
		@Nullable String race,
		@Nullable String gender,
		@Nullable String cpf,
		@Nullable String rg,
		@Nullable String nationality,
		@Nullable String maritalStatus,
		@Nullable String religion,
		@Nullable String church,
		@Nullable String healthReport,
		@Nullable String medication,
		@Nullable String medicalObservation,
		@Nullable String birthCertificateNumber,
		Instant createdAt,
		Instant updatedAt) {
}
