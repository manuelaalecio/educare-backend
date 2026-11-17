package com.manuelaalecio.educare_backend.guardian.application;

import java.time.LocalDate;

/**
 * The data of a guardian as received by the use cases, from this module's API or from another module (a new
 * guardian in the body of a child). The marital status is the name of a constant, already validated by the request;
 * {@code notWorking} {@code null} counts as {@code false}.
 */
public record GuardianInput(
		String name,
		String maritalStatus,
		LocalDate birthDate,
		String cpf,
		String rg,
		String phone,
		Boolean notWorking,
		String workplace,
		String workAddress,
		String workPhone) {
}
