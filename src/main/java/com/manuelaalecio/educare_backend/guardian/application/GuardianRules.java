package com.manuelaalecio.educare_backend.guardian.application;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

import com.manuelaalecio.educare_backend.guardian.domain.Guardian;
import com.manuelaalecio.educare_backend.guardian.domain.GuardianBirthDateInFutureException;
import com.manuelaalecio.educare_backend.guardian.domain.GuardianCpfAlreadyInUseException;
import com.manuelaalecio.educare_backend.guardian.domain.GuardianDetails;
import com.manuelaalecio.educare_backend.guardian.domain.GuardianRepository;
import com.manuelaalecio.educare_backend.guardian.domain.MaritalStatus;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * The rules shared by the creation ({@link GuardianRegistry}) and the update ({@link GuardianService}) of a
 * guardian.
 */
final class GuardianRules {

	/**
	 * The institution's time zone: "today" follows it, not the UTC application clock, so the date does not move
	 * forward in the evening.
	 */
	static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");

	private GuardianRules() {
	}

	static GuardianDetails toDetails(GuardianInput input) {
		return new GuardianDetails(input.name(),
				input.maritalStatus() == null ? null : MaritalStatus.valueOf(input.maritalStatus()), input.birthDate(),
				input.cpf(), input.rg(), input.phone(), Boolean.TRUE.equals(input.notWorking()), input.workplace(),
				input.workAddress(), input.workPhone());
	}

	/**
	 * @throws GuardianBirthDateInFutureException when the birth date is after today in the institution's time zone
	 */
	static void checkBirthDate(GuardianDetails details, Clock clock) {
		if (details.birthDate() != null && details.birthDate().isAfter(LocalDate.now(clock.withZone(ZONE)))) {
			throw new GuardianBirthDateInFutureException();
		}
	}

	/**
	 * Flushes so a concurrent save of the same CPF violates its unique index here, where it can still become a
	 * conflict. Any other violation would be a bug, not a conflict, so it is rethrown.
	 */
	static Guardian saveAndFlush(GuardianRepository repository, Guardian guardian) {
		try {
			return repository.saveAndFlush(guardian);
		}
		catch (DataIntegrityViolationException ex) {
			if (GuardianRepository.CPF_UNIQUE_CONSTRAINT.equals(violatedConstraint(ex))) {
				throw new GuardianCpfAlreadyInUseException();
			}
			throw ex;
		}
	}

	private static String violatedConstraint(Throwable ex) {
		for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
			if (cause instanceof ConstraintViolationException violation) {
				return violation.getConstraintName();
			}
		}
		return null;
	}

}
