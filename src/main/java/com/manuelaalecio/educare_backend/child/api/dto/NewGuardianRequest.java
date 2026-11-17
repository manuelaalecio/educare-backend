package com.manuelaalecio.educare_backend.child.api.dto;

import java.time.LocalDate;

import com.manuelaalecio.educare_backend.shared.validation.RequiredUnless;
import com.manuelaalecio.educare_backend.shared.validation.ValidCpf;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * A new guardian in the body of a child ({@code guardians[i].guardian}). Same fields and rules as the body of
 * {@code PUT /api/v1/guardians/{id}} ({@code GuardianRequest}, which this module cannot use): the work fields are
 * required unless {@code notWorking} is {@code true}, in which case they are discarded, and the compact constructor
 * strips every text and turns blank texts into {@code null} before Bean Validation runs.
 */
@RequiredUnless(flag = "notWorking", fields = {"workplace", "workAddress", "workPhone"})
public record NewGuardianRequest(

		@NotBlank(message = "é obrigatório")
		@Size(max = 150, message = "deve ter no máximo {max} caracteres")
		String name,

		@Pattern(regexp = "SINGLE|MARRIED|STABLE_UNION|DIVORCED|WIDOWED",
				message = "deve ser SINGLE, MARRIED, STABLE_UNION, DIVORCED ou WIDOWED")
		String maritalStatus,

		LocalDate birthDate,

		@ValidCpf
		String cpf,

		@Size(max = 20, message = "deve ter no máximo {max} caracteres")
		@Pattern(regexp = ".*[A-Za-z0-9].*", message = "deve ter ao menos uma letra ou dígito")
		String rg,

		@NotBlank(message = "é obrigatório")
		@Pattern(regexp = "^[0-9]{10,11}$", message = "deve ter 10 ou 11 dígitos, com o DDD")
		String phone,

		Boolean notWorking,

		@Size(max = 150, message = "deve ter no máximo {max} caracteres")
		String workplace,

		@Size(max = 200, message = "deve ter no máximo {max} caracteres")
		String workAddress,

		@Pattern(regexp = "^[0-9]{10,11}$", message = "deve ter 10 ou 11 dígitos, com o DDD")
		String workPhone) {

	public NewGuardianRequest {
		name = clean(name);
		maritalStatus = clean(maritalStatus);
		cpf = clean(cpf);
		rg = clean(rg);
		phone = clean(phone);
		if (Boolean.TRUE.equals(notWorking)) {
			workplace = null;
			workAddress = null;
			workPhone = null;
		}
		else {
			workplace = clean(workplace);
			workAddress = clean(workAddress);
			workPhone = clean(workPhone);
		}
	}

	private static String clean(String value) {
		return value == null || value.isBlank() ? null : value.strip();
	}

}
