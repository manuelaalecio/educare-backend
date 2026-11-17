package com.manuelaalecio.educare_backend.guardian.domain;

import java.time.LocalDate;

/**
 * The editable data of a guardian, as received for creation and for the full replacement on update. Texts may still
 * have leading and trailing spaces and documents their punctuation: {@link Guardian} normalizes and validates them.
 */
public record GuardianDetails(
		String name,
		MaritalStatus maritalStatus,
		LocalDate birthDate,
		String cpf,
		String rg,
		String phone,
		boolean notWorking,
		String workplace,
		String workAddress,
		String workPhone) {
}
