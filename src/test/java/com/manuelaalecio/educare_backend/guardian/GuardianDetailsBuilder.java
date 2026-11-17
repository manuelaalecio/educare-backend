package com.manuelaalecio.educare_backend.guardian;

import java.time.LocalDate;

import com.manuelaalecio.educare_backend.guardian.domain.GuardianDetails;
import com.manuelaalecio.educare_backend.guardian.domain.MaritalStatus;

/**
 * Builds {@link GuardianDetails} for tests, starting from the required data of a valid working guardian (optional
 * fields empty). Each setter returns the builder, so a test only states the fields it cares about.
 */
public final class GuardianDetailsBuilder {

	private String name = "Maria Souza";
	private MaritalStatus maritalStatus;
	private LocalDate birthDate;
	private String cpf;
	private String rg;
	private String phone = "11912345678";
	private boolean notWorking;
	private String workplace = "Padaria Central";
	private String workAddress = "Rua das Flores, 100";
	private String workPhone = "1133334444";

	private GuardianDetailsBuilder() {
	}

	public static GuardianDetailsBuilder aGuardian() {
		return new GuardianDetailsBuilder();
	}

	/**
	 * Every optional field filled with valid values.
	 */
	public static GuardianDetailsBuilder aCompleteGuardian() {
		return aGuardian().maritalStatus(MaritalStatus.MARRIED)
			.birthDate(LocalDate.of(1990, 5, 10))
			.cpf("529.982.247-25")
			.rg("12.345.678-x");
	}

	public GuardianDetailsBuilder name(String name) {
		this.name = name;
		return this;
	}

	public GuardianDetailsBuilder maritalStatus(MaritalStatus maritalStatus) {
		this.maritalStatus = maritalStatus;
		return this;
	}

	public GuardianDetailsBuilder birthDate(LocalDate birthDate) {
		this.birthDate = birthDate;
		return this;
	}

	public GuardianDetailsBuilder cpf(String cpf) {
		this.cpf = cpf;
		return this;
	}

	public GuardianDetailsBuilder rg(String rg) {
		this.rg = rg;
		return this;
	}

	public GuardianDetailsBuilder phone(String phone) {
		this.phone = phone;
		return this;
	}

	public GuardianDetailsBuilder notWorking(boolean notWorking) {
		this.notWorking = notWorking;
		return this;
	}

	public GuardianDetailsBuilder workplace(String workplace) {
		this.workplace = workplace;
		return this;
	}

	public GuardianDetailsBuilder workAddress(String workAddress) {
		this.workAddress = workAddress;
		return this;
	}

	public GuardianDetailsBuilder workPhone(String workPhone) {
		this.workPhone = workPhone;
		return this;
	}

	public GuardianDetails build() {
		return new GuardianDetails(name, maritalStatus, birthDate, cpf, rg, phone, notWorking, workplace, workAddress,
				workPhone);
	}

}
