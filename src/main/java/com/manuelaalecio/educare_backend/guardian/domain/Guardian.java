package com.manuelaalecio.educare_backend.guardian.domain;

import java.time.LocalDate;
import java.util.regex.Pattern;

import com.manuelaalecio.educare_backend.shared.persistence.BaseEntity;
import com.manuelaalecio.educare_backend.shared.validation.Cpf;
import com.manuelaalecio.educare_backend.shared.validation.Rg;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A guardian of one or more children. Holds personal data (LGPD): no message of this class carries a value, only
 * the field name.
 * <p>
 * The data is always applied as a whole ({@link #Guardian(GuardianDetails)} and {@link #update(GuardianDetails)}),
 * normalized idempotently (texts without leading and trailing spaces, blank optional texts as {@code null}, CPF with
 * digits only, RG with letters and digits in upper case) and validated, so no caller can store an invalid state.
 * The work fields are required while the guardian works and discarded when {@code notWorking} is {@code true}. The
 * birth date is checked against the clock by the use cases, not here. The request validation repeats these rules
 * on purpose: it is what turns them into {@code 400} responses with one entry per field.
 */
@Entity
@Table(name = "guardians")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Guardian extends BaseEntity {

	public static final int NAME_MAX_LENGTH = 150;
	public static final int WORKPLACE_MAX_LENGTH = 150;
	public static final int WORK_ADDRESS_MAX_LENGTH = 200;
	public static final int RG_MAX_LENGTH = 20;

	private static final Pattern PHONE = Pattern.compile("^[0-9]{10,11}$");

	@Column(name = "name", nullable = false, length = NAME_MAX_LENGTH)
	private String name;

	@Enumerated(EnumType.STRING)
	@Column(name = "marital_status", length = 20)
	private MaritalStatus maritalStatus;

	@Column(name = "birth_date")
	private LocalDate birthDate;

	@Column(name = "cpf", length = 11)
	private String cpf;

	@Column(name = "rg", length = RG_MAX_LENGTH)
	private String rg;

	@Column(name = "phone", nullable = false, length = 11)
	private String phone;

	@Column(name = "not_working", nullable = false)
	private boolean notWorking;

	@Column(name = "workplace", length = WORKPLACE_MAX_LENGTH)
	private String workplace;

	@Column(name = "work_address", length = WORK_ADDRESS_MAX_LENGTH)
	private String workAddress;

	@Column(name = "work_phone", length = 11)
	private String workPhone;

	public Guardian(GuardianDetails details) {
		update(details);
	}

	/**
	 * Replaces every editable field: an optional field missing from {@code details} is cleared. Nothing changes
	 * when the details are invalid.
	 *
	 * @throws IllegalArgumentException when a field is invalid; the message names the field, never its value
	 */
	public void update(GuardianDetails details) {
		if (details == null) {
			throw new IllegalArgumentException("Dados do responsável são obrigatórios");
		}
		String newName = required(details.name(), "Nome", NAME_MAX_LENGTH);
		String newCpf = cpf(details.cpf());
		String newRg = rg(details.rg());
		String newPhone = phone(details.phone(), "Telefone");
		String newWorkplace = null;
		String newWorkAddress = null;
		String newWorkPhone = null;
		if (!details.notWorking()) {
			newWorkplace = required(details.workplace(), "Local de trabalho", WORKPLACE_MAX_LENGTH);
			newWorkAddress = required(details.workAddress(), "Endereço do trabalho", WORK_ADDRESS_MAX_LENGTH);
			newWorkPhone = phone(details.workPhone(), "Telefone do trabalho");
		}

		this.name = newName;
		this.maritalStatus = details.maritalStatus();
		this.birthDate = details.birthDate();
		this.cpf = newCpf;
		this.rg = newRg;
		this.phone = newPhone;
		this.notWorking = details.notWorking();
		this.workplace = newWorkplace;
		this.workAddress = newWorkAddress;
		this.workPhone = newWorkPhone;
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

	private static String phone(String value, String field) {
		String stripped = required(value, field, Integer.MAX_VALUE);
		if (!PHONE.matcher(stripped).matches()) {
			throw new IllegalArgumentException(field + " deve ter 10 ou 11 dígitos");
		}
		return stripped;
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

}
