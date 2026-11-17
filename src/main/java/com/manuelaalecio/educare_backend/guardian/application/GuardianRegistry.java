package com.manuelaalecio.educare_backend.guardian.application;

import java.time.Clock;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.manuelaalecio.educare_backend.guardian.domain.Guardian;
import com.manuelaalecio.educare_backend.guardian.domain.GuardianCpfAlreadyInUseException;
import com.manuelaalecio.educare_backend.guardian.domain.GuardianDetails;
import com.manuelaalecio.educare_backend.guardian.domain.GuardianRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Access to the guardians for other modules (the {@code child} module creates guardians in the body of a child and
 * shows their contacts), without exposing the repository. Separate from {@link GuardianService}, which depends on a
 * port implemented by {@code child}, so the beans of the two modules never depend on each other in a cycle. Runs in
 * the caller's transaction: a refusal later in the caller's use case undoes the guardians created here.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class GuardianRegistry {

	private final GuardianRepository guardianRepository;
	private final Clock clock;

	/**
	 * Creates a guardian. Errors name the fields relative to the guardian ({@code birthDate}, {@code cpf}): the
	 * caller re-anchors them in its body.
	 *
	 * @return the id of the new guardian
	 * @throws com.manuelaalecio.educare_backend.guardian.domain.GuardianBirthDateInFutureException when the birth
	 * date is in the future
	 * @throws GuardianCpfAlreadyInUseException when another guardian has the same CPF
	 */
	public UUID create(GuardianInput input) {
		GuardianDetails details = GuardianRules.toDetails(input);
		GuardianRules.checkBirthDate(details, clock);
		Guardian guardian = new Guardian(details);
		if (guardian.getCpf() != null && guardianRepository.existsByCpf(guardian.getCpf())) {
			throw new GuardianCpfAlreadyInUseException();
		}
		return GuardianRules.saveAndFlush(guardianRepository, guardian).getId();
	}

	/**
	 * Looks up the contacts of several guardians in a single query.
	 *
	 * @return the contacts found, by id; ids of guardians that do not exist are missing from the map
	 */
	@Transactional(readOnly = true)
	public Map<UUID, GuardianContact> findContacts(Collection<UUID> ids) {
		if (ids.isEmpty()) {
			return Map.of();
		}
		return guardianRepository.findAllById(ids)
			.stream()
			.map(GuardianRegistry::toContact)
			.collect(Collectors.toMap(GuardianContact::id, Function.identity()));
	}

	private static GuardianContact toContact(Guardian guardian) {
		return new GuardianContact(guardian.getId(), guardian.getName(), guardian.getPhone(), guardian.isNotWorking(),
				guardian.getWorkplace(), guardian.getWorkPhone());
	}

}
