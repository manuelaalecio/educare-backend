package com.manuelaalecio.educare_backend.child.domain;

import java.time.LocalDate;

/**
 * The editable data of a child, as received for creation and for the full replacement on update. Texts may still
 * have leading and trailing spaces and documents their punctuation: {@link Child} normalizes and validates them.
 */
public record ChildDetails(
		String name,
		LocalDate birthDate,
		String cep,
		String address,
		String neighborhood,
		String addressNumber,
		String addressComplement,
		String city,
		BrazilianState state,
		String birthplace,
		Race race,
		Gender gender,
		String cpf,
		String rg,
		String nationality,
		MaritalStatus maritalStatus,
		String religion,
		String church,
		String healthReport,
		String medication,
		String medicalObservation,
		String birthCertificateNumber) {
}
