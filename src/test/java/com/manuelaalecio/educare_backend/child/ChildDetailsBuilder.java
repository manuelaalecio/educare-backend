package com.manuelaalecio.educare_backend.child;

import java.time.LocalDate;

import com.manuelaalecio.educare_backend.child.domain.BrazilianState;
import com.manuelaalecio.educare_backend.child.domain.ChildDetails;
import com.manuelaalecio.educare_backend.child.domain.Gender;
import com.manuelaalecio.educare_backend.child.domain.MaritalStatus;
import com.manuelaalecio.educare_backend.child.domain.Race;

/**
 * Builds {@link ChildDetails} for tests, starting from the required data of a valid child (all optional fields
 * empty). Each setter returns the builder, so a test only states the fields it cares about.
 */
public final class ChildDetailsBuilder {

	public static final String BIRTH_CERTIFICATE_NUMBER = "10453901552013100012021000012321";

	private String name = "Maria Clara Souza";
	private LocalDate birthDate = LocalDate.of(2018, 5, 10);
	private String cep = "01310-100";
	private String address = "Avenida Paulista";
	private String neighborhood = "Bela Vista";
	private String addressNumber = "1000";
	private String addressComplement;
	private String city = "São Paulo";
	private BrazilianState state = BrazilianState.SP;
	private String birthplace;
	private Race race;
	private Gender gender;
	private String cpf;
	private String rg;
	private String nationality;
	private MaritalStatus maritalStatus;
	private String religion;
	private String church;
	private String healthReport;
	private String medication;
	private String medicalObservation;
	private String birthCertificateNumber;

	private ChildDetailsBuilder() {
	}

	public static ChildDetailsBuilder aChild() {
		return new ChildDetailsBuilder();
	}

	/**
	 * Every optional field filled with valid values.
	 */
	public static ChildDetailsBuilder aCompleteChild() {
		return aChild().addressComplement("Apto 12")
			.birthplace("São Paulo - SP")
			.race(Race.BROWN)
			.gender(Gender.FEMALE)
			.cpf("529.982.247-25")
			.rg("12.345.678-9")
			.nationality("Brasileira")
			.maritalStatus(MaritalStatus.SINGLE)
			.religion("Católica")
			.church("Paróquia São José")
			.healthReport("Asma leve")
			.medication("Salbutamol em crises")
			.medicalObservation("Evitar poeira")
			.birthCertificateNumber(BIRTH_CERTIFICATE_NUMBER);
	}

	public ChildDetailsBuilder name(String name) {
		this.name = name;
		return this;
	}

	public ChildDetailsBuilder birthDate(LocalDate birthDate) {
		this.birthDate = birthDate;
		return this;
	}

	public ChildDetailsBuilder cep(String cep) {
		this.cep = cep;
		return this;
	}

	public ChildDetailsBuilder address(String address) {
		this.address = address;
		return this;
	}

	public ChildDetailsBuilder neighborhood(String neighborhood) {
		this.neighborhood = neighborhood;
		return this;
	}

	public ChildDetailsBuilder addressNumber(String addressNumber) {
		this.addressNumber = addressNumber;
		return this;
	}

	public ChildDetailsBuilder addressComplement(String addressComplement) {
		this.addressComplement = addressComplement;
		return this;
	}

	public ChildDetailsBuilder city(String city) {
		this.city = city;
		return this;
	}

	public ChildDetailsBuilder state(BrazilianState state) {
		this.state = state;
		return this;
	}

	public ChildDetailsBuilder birthplace(String birthplace) {
		this.birthplace = birthplace;
		return this;
	}

	public ChildDetailsBuilder race(Race race) {
		this.race = race;
		return this;
	}

	public ChildDetailsBuilder gender(Gender gender) {
		this.gender = gender;
		return this;
	}

	public ChildDetailsBuilder cpf(String cpf) {
		this.cpf = cpf;
		return this;
	}

	public ChildDetailsBuilder rg(String rg) {
		this.rg = rg;
		return this;
	}

	public ChildDetailsBuilder nationality(String nationality) {
		this.nationality = nationality;
		return this;
	}

	public ChildDetailsBuilder maritalStatus(MaritalStatus maritalStatus) {
		this.maritalStatus = maritalStatus;
		return this;
	}

	public ChildDetailsBuilder religion(String religion) {
		this.religion = religion;
		return this;
	}

	public ChildDetailsBuilder church(String church) {
		this.church = church;
		return this;
	}

	public ChildDetailsBuilder healthReport(String healthReport) {
		this.healthReport = healthReport;
		return this;
	}

	public ChildDetailsBuilder medication(String medication) {
		this.medication = medication;
		return this;
	}

	public ChildDetailsBuilder medicalObservation(String medicalObservation) {
		this.medicalObservation = medicalObservation;
		return this;
	}

	public ChildDetailsBuilder birthCertificateNumber(String birthCertificateNumber) {
		this.birthCertificateNumber = birthCertificateNumber;
		return this;
	}

	public ChildDetails build() {
		return new ChildDetails(name, birthDate, cep, address, neighborhood, addressNumber, addressComplement, city,
				state, birthplace, race, gender, cpf, rg, nationality, maritalStatus, religion, church, healthReport,
				medication, medicalObservation, birthCertificateNumber);
	}

}
