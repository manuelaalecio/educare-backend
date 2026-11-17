package com.manuelaalecio.educare_backend.child.api.dto;

import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * One guardian of the child in the body: a guardian already registered ({@code guardianId}) or a new one
 * ({@code guardian}), exactly one of them (checked by {@code @OneGuardianSource} on the list element), always with
 * the relationship with this child.
 */
public record ChildGuardianRequest(

		UUID guardianId,

		@Valid
		NewGuardianRequest guardian,

		@NotBlank(message = "é obrigatório")
		@Pattern(regexp = "MOTHER|FATHER|GRANDMOTHER|GRANDFATHER|AUNT|UNCLE|SISTER|BROTHER|STEPMOTHER|STEPFATHER"
				+ "|LEGAL_GUARDIAN|OTHER",
				message = "deve ser MOTHER, FATHER, GRANDMOTHER, GRANDFATHER, AUNT, UNCLE, SISTER, BROTHER, STEPMOTHER, "
						+ "STEPFATHER, LEGAL_GUARDIAN ou OTHER")
		String relationship) {

	public ChildGuardianRequest {
		relationship = relationship == null || relationship.isBlank() ? null : relationship.strip();
	}

}
