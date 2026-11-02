package com.manuelaalecio.educare_backend.child.domain;

import java.time.LocalDate;

import com.manuelaalecio.educare_backend.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A child served by the institution. Holds personal data of a minor (LGPD, art. 14): no message of this class
 * carries a value, only the field name.
 * <p>
 * The data is always applied as a whole ({@link #Child(ChildDetails)} and {@link #update(ChildDetails)}), normalized
 * idempotently (texts without leading and trailing spaces, blank optional texts as {@code null}, CEP, CPF and birth
 * certificate number with digits only, RG with letters and digits in upper case) and validated, so no caller can
 * store an invalid state. The request validation repeats these rules on purpose: it is what turns them into
 * {@code 400} responses with one entry per field.
 */
@Entity
@Table(name = "children")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Child extends BaseEntity {

	public static final int NAME_MAX_LENGTH = 150;
	public static final int BIRTHPLACE_MAX_LENGTH = 150;
	public static final int ADDRESS_MAX_LENGTH = 200;
	public static final int NEIGHBORHOOD_MAX_LENGTH = 100;
	public static final int ADDRESS_NUMBER_MAX_LENGTH = 20;
	public static final int ADDRESS_COMPLEMENT_MAX_LENGTH = 100;
	public static final int CITY_MAX_LENGTH = 100;
	public static final int RG_MAX_LENGTH = 20;
	public static final int NATIONALITY_MAX_LENGTH = 100;
	public static final int RELIGION_MAX_LENGTH = 100;
	public static final int CHURCH_MAX_LENGTH = 150;
	public static final int MEDICATION_MAX_LENGTH = 1000;
	public static final int HEALTH_REPORT_MAX_LENGTH = 2000;
	public static final int MEDICAL_OBSERVATION_MAX_LENGTH = 2000;

	@Column(name = "name", nullable = false, length = NAME_MAX_LENGTH)
	private String name;

	@Column(name = "birth_date", nullable = false)
	private LocalDate birthDate;

	@Column(name = "cep", nullable = false, length = 8)
	private String cep;

	@Column(name = "address", nullable = false, length = ADDRESS_MAX_LENGTH)
	private String address;

	@Column(name = "neighborhood", nullable = false, length = NEIGHBORHOOD_MAX_LENGTH)
	private String neighborhood;

	@Column(name = "address_number", nullable = false, length = ADDRESS_NUMBER_MAX_LENGTH)
	private String addressNumber;

	@Column(name = "address_complement", length = ADDRESS_COMPLEMENT_MAX_LENGTH)
	private String addressComplement;

	@Column(name = "city", nullable = false, length = CITY_MAX_LENGTH)
	private String city;

	@Enumerated(EnumType.STRING)
	@Column(name = "state", nullable = false, length = 2)
	private BrazilianState state;

	@Column(name = "birthplace", length = BIRTHPLACE_MAX_LENGTH)
	private String birthplace;

	@Enumerated(EnumType.STRING)
	@Column(name = "race", length = 20)
	private Race race;

	@Enumerated(EnumType.STRING)
	@Column(name = "gender", length = 20)
	private Gender gender;

	@Column(name = "cpf", length = 11)
	private String cpf;

	@Column(name = "rg", length = RG_MAX_LENGTH)
	private String rg;

	@Column(name = "nationality", length = NATIONALITY_MAX_LENGTH)
	private String nationality;

	@Enumerated(EnumType.STRING)
	@Column(name = "marital_status", length = 20)
	private MaritalStatus maritalStatus;

	@Column(name = "religion", length = RELIGION_MAX_LENGTH)
	private String religion;

	@Column(name = "church", length = CHURCH_MAX_LENGTH)
	private String church;

	@Column(name = "health_report", length = HEALTH_REPORT_MAX_LENGTH)
	private String healthReport;

	@Column(name = "medication", length = MEDICATION_MAX_LENGTH)
	private String medication;

	@Column(name = "medical_observation", length = MEDICAL_OBSERVATION_MAX_LENGTH)
	private String medicalObservation;

	@Column(name = "birth_certificate_number", length = BirthRegistrationNumber.LENGTH)
	private String birthCertificateNumber;

	public Child(ChildDetails details) {
		update(details);
	}

	/**
	 * Replaces every editable field: an optional field missing from {@code details} is cleared. Nothing changes
	 * when the details are invalid.
	 *
	 * @throws IllegalArgumentException when a field is invalid; the message names the field, never its value
	 */
	public void update(ChildDetails details) {
		if (details == null) {
			throw new IllegalArgumentException("Dados da criança são obrigatórios");
		}
		if (details.birthDate() == null) {
			throw new IllegalArgumentException("Data de nascimento é obrigatória");
		}
		if (details.state() == null) {
			throw new IllegalArgumentException("UF é obrigatória");
		}
		String newName = required(details.name(), "Nome", NAME_MAX_LENGTH);
		String newCep = cep(details.cep());
		String newAddress = required(details.address(), "Endereço", ADDRESS_MAX_LENGTH);
		String newNeighborhood = required(details.neighborhood(), "Bairro", NEIGHBORHOOD_MAX_LENGTH);
		String newAddressNumber = required(details.addressNumber(), "Número", ADDRESS_NUMBER_MAX_LENGTH);
		String newAddressComplement = optional(details.addressComplement(), "Complemento",
				ADDRESS_COMPLEMENT_MAX_LENGTH);
		String newCity = required(details.city(), "Cidade", CITY_MAX_LENGTH);
		String newBirthplace = optional(details.birthplace(), "Naturalidade", BIRTHPLACE_MAX_LENGTH);
		String newCpf = cpf(details.cpf());
		String newRg = rg(details.rg());
		String newNationality = optional(details.nationality(), "Nacionalidade", NATIONALITY_MAX_LENGTH);
		String newReligion = optional(details.religion(), "Religião", RELIGION_MAX_LENGTH);
		String newChurch = optional(details.church(), "Igreja", CHURCH_MAX_LENGTH);
		String newHealthReport = optional(details.healthReport(), "Laudo de saúde", HEALTH_REPORT_MAX_LENGTH);
		String newMedication = optional(details.medication(), "Medicação", MEDICATION_MAX_LENGTH);
		String newMedicalObservation = optional(details.medicalObservation(), "Observação médica",
				MEDICAL_OBSERVATION_MAX_LENGTH);
		String newBirthCertificateNumber = birthCertificateNumber(details.birthCertificateNumber());

		this.name = newName;
		this.birthDate = details.birthDate();
		this.cep = newCep;
		this.address = newAddress;
		this.neighborhood = newNeighborhood;
		this.addressNumber = newAddressNumber;
		this.addressComplement = newAddressComplement;
		this.city = newCity;
		this.state = details.state();
		this.birthplace = newBirthplace;
		this.race = details.race();
		this.gender = details.gender();
		this.cpf = newCpf;
		this.rg = newRg;
		this.nationality = newNationality;
		this.maritalStatus = details.maritalStatus();
		this.religion = newReligion;
		this.church = newChurch;
		this.healthReport = newHealthReport;
		this.medication = newMedication;
		this.medicalObservation = newMedicalObservation;
		this.birthCertificateNumber = newBirthCertificateNumber;
	}

	private static String required(String value, String field, int maxLength) {
		String stripped = optional(value, field, maxLength);
		if (stripped == null) {
			throw new IllegalArgumentException(field + " é obrigatório");
		}
		return stripped;
	}

	/**
	 * @return the value without leading and trailing spaces, or {@code null} when it is missing or blank
	 */
	private static String optional(String value, String field, int maxLength) {
		if (value == null || value.isBlank()) {
			return null;
		}
		String stripped = value.strip();
		if (stripped.length() > maxLength) {
			throw new IllegalArgumentException(field + " deve ter no máximo " + maxLength + " caracteres");
		}
		return stripped;
	}

	private static String cep(String value) {
		String stripped = required(value, "CEP", Integer.MAX_VALUE);
		if (!Cep.isValid(stripped)) {
			throw new IllegalArgumentException("CEP inválido");
		}
		return Cep.normalize(stripped);
	}

	private static String cpf(String value) {
		String stripped = optional(value, "CPF", Integer.MAX_VALUE);
		if (stripped == null) {
			return null;
		}
		if (!Cpf.isValid(stripped)) {
			throw new IllegalArgumentException("CPF inválido");
		}
		return Cpf.normalize(stripped);
	}

	private static String rg(String value) {
		String stripped = optional(value, "RG", RG_MAX_LENGTH);
		if (stripped == null) {
			return null;
		}
		String normalized = Rg.normalize(stripped);
		if (normalized.isEmpty()) {
			throw new IllegalArgumentException("RG deve ter ao menos uma letra ou dígito");
		}
		return normalized;
	}

	private static String birthCertificateNumber(String value) {
		String stripped = optional(value, "Matrícula da certidão", Integer.MAX_VALUE);
		if (stripped == null) {
			return null;
		}
		if (!BirthRegistrationNumber.isValid(stripped)) {
			throw new IllegalArgumentException("Matrícula da certidão deve ter 32 dígitos");
		}
		return BirthRegistrationNumber.normalize(stripped);
	}

}
