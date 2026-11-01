package com.manuelaalecio.educare_backend.child.api.dto;

import java.time.LocalDate;
import java.util.Locale;

import com.manuelaalecio.educare_backend.child.api.ValidBirthRegistrationNumber;
import com.manuelaalecio.educare_backend.child.api.ValidCpf;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /api/v1/children} and {@code PUT /api/v1/children/{id}}: the update replaces the whole child,
 * so both accept the same fields and rules. The compact constructor strips every text and turns blank texts into
 * {@code null} before Bean Validation runs, so "required" and the maximum lengths are checked on the value that will
 * be stored; it also upper-cases the state.
 */
public record ChildRequest(

		@NotBlank(message = "é obrigatório")
		@Size(max = 150, message = "deve ter no máximo {max} caracteres")
		String name,

		@NotNull(message = "é obrigatória")
		LocalDate birthDate,

		@NotBlank(message = "é obrigatório")
		@Pattern(regexp = "^[0-9]{5}-?[0-9]{3}$", message = "deve ter 8 dígitos, com ou sem hífen")
		String cep,

		@NotBlank(message = "é obrigatório")
		@Size(max = 200, message = "deve ter no máximo {max} caracteres")
		String address,

		@NotBlank(message = "é obrigatório")
		@Size(max = 100, message = "deve ter no máximo {max} caracteres")
		String neighborhood,

		@NotBlank(message = "é obrigatório")
		@Size(max = 20, message = "deve ter no máximo {max} caracteres")
		String addressNumber,

		@Size(max = 100, message = "deve ter no máximo {max} caracteres")
		String addressComplement,

		@NotBlank(message = "é obrigatória")
		@Size(max = 100, message = "deve ter no máximo {max} caracteres")
		String city,

		@NotBlank(message = "é obrigatória")
		@Pattern(regexp = "AC|AL|AP|AM|BA|CE|DF|ES|GO|MA|MT|MS|MG|PA|PB|PR|PE|PI|RJ|RN|RS|RO|RR|SC|SP|SE|TO",
				message = "deve ser a sigla de uma UF")
		String state,

		@Size(max = 150, message = "deve ter no máximo {max} caracteres")
		String birthplace,

		@Pattern(regexp = "WHITE|BLACK|BROWN|YELLOW|INDIGENOUS",
				message = "deve ser WHITE, BLACK, BROWN, YELLOW ou INDIGENOUS")
		String race,

		@Pattern(regexp = "MALE|FEMALE|OTHER", message = "deve ser MALE, FEMALE ou OTHER")
		String gender,

		@ValidCpf
		String cpf,

		@Size(max = 20, message = "deve ter no máximo {max} caracteres")
		@Pattern(regexp = ".*[A-Za-z0-9].*", message = "deve ter ao menos uma letra ou dígito")
		String rg,

		@Size(max = 100, message = "deve ter no máximo {max} caracteres")
		String nationality,

		@Pattern(regexp = "SINGLE|MARRIED|STABLE_UNION|DIVORCED|WIDOWED",
				message = "deve ser SINGLE, MARRIED, STABLE_UNION, DIVORCED ou WIDOWED")
		String maritalStatus,

		@Size(max = 100, message = "deve ter no máximo {max} caracteres")
		String religion,

		@Size(max = 150, message = "deve ter no máximo {max} caracteres")
		String church,

		@Size(max = 2000, message = "deve ter no máximo {max} caracteres")
		String healthReport,

		@Size(max = 1000, message = "deve ter no máximo {max} caracteres")
		String medication,

		@Size(max = 2000, message = "deve ter no máximo {max} caracteres")
		String medicalObservation,

		@ValidBirthRegistrationNumber
		String birthCertificateNumber) {

	public ChildRequest {
		name = clean(name);
		cep = clean(cep);
		address = clean(address);
		neighborhood = clean(neighborhood);
		addressNumber = clean(addressNumber);
		addressComplement = clean(addressComplement);
		city = clean(city);
		state = clean(state);
		if (state != null) {
			state = state.toUpperCase(Locale.ROOT);
		}
		birthplace = clean(birthplace);
		race = clean(race);
		gender = clean(gender);
		cpf = clean(cpf);
		rg = clean(rg);
		nationality = clean(nationality);
		maritalStatus = clean(maritalStatus);
		religion = clean(religion);
		church = clean(church);
		healthReport = clean(healthReport);
		medication = clean(medication);
		medicalObservation = clean(medicalObservation);
		birthCertificateNumber = clean(birthCertificateNumber);
	}

	private static String clean(String value) {
		return value == null || value.isBlank() ? null : value.strip();
	}

}
