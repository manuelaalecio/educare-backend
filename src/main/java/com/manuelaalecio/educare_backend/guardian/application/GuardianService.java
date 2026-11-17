package com.manuelaalecio.educare_backend.guardian.application;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import com.manuelaalecio.educare_backend.guardian.domain.Guardian;
import com.manuelaalecio.educare_backend.guardian.domain.GuardianCpfAlreadyInUseException;
import com.manuelaalecio.educare_backend.guardian.domain.GuardianDetails;
import com.manuelaalecio.educare_backend.guardian.domain.GuardianNotFoundException;
import com.manuelaalecio.educare_backend.guardian.domain.GuardianRepository;
import com.manuelaalecio.educare_backend.guardian.domain.GuardianSpecifications;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Use cases of the guardians registry exposed by this module's API. Guardians are created in the body of a child
 * (see {@link GuardianRegistry}); here they are listed, read and updated. The linked children come from the
 * {@link GuardianChildrenFinder} port, implemented by the {@code child} module.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class GuardianService {

	/**
	 * A search made of exactly 11 digits is a full CPF: compared for equality, never as part of a name.
	 */
	private static final Pattern FULL_CPF = Pattern.compile("^[0-9]{11}$");

	private final GuardianRepository guardianRepository;
	private final GuardianChildrenFinder childrenFinder;
	private final Clock clock;

	/**
	 * A page of guardians, each with its children: the page, its count and the children of the page are read in a
	 * fixed number of queries, whatever the page size.
	 *
	 * @param q a full CPF (11 digits), matched exactly; otherwise part of the name, compared ignoring case and
	 * accents, with {@code %} and {@code _} taken literally; ignored when {@code null} or blank
	 */
	@Transactional(readOnly = true)
	public Page<GuardianSummaryView> list(String q, Pageable pageable) {
		Specification<Guardian> filter = Specification.unrestricted();
		if (q != null && !q.isBlank()) {
			String search = q.strip();
			filter = FULL_CPF.matcher(search).matches() ? GuardianSpecifications.cpfEquals(search)
					: GuardianSpecifications.nameMatches(likePattern(search));
		}
		Page<Guardian> page = guardianRepository.findAll(filter, pageable);
		if (page.isEmpty()) {
			return page.map(guardian -> new GuardianSummaryView(guardian, List.of()));
		}
		Map<UUID, List<GuardianChildRef>> children = childrenFinder
			.findChildren(page.map(Guardian::getId).getContent());
		return page.map(guardian -> new GuardianSummaryView(guardian,
				children.getOrDefault(guardian.getId(), List.of())));
	}

	@Transactional(readOnly = true)
	public GuardianView findById(UUID id) {
		return withChildren(load(id));
	}

	/**
	 * Replaces every field of the guardian; the links and relationships with children do not change. The CPF is
	 * checked on a detached copy before the managed entity changes: the check is a query, which flushes pending
	 * changes, so a repeated CPF set beforehand would violate the index during the check instead of becoming a
	 * conflict.
	 */
	public GuardianView update(UUID id, GuardianInput input) {
		Guardian guardian = load(id);
		GuardianDetails details = GuardianRules.toDetails(input);
		GuardianRules.checkBirthDate(details, clock);
		Guardian candidate = new Guardian(details);
		if (candidate.getCpf() != null && guardianRepository.existsByCpfAndIdNot(candidate.getCpf(), id)) {
			throw new GuardianCpfAlreadyInUseException();
		}
		guardian.update(details);
		return withChildren(GuardianRules.saveAndFlush(guardianRepository, guardian));
	}

	private Guardian load(UUID id) {
		return guardianRepository.findById(id).orElseThrow(GuardianNotFoundException::new);
	}

	private GuardianView withChildren(Guardian guardian) {
		return new GuardianView(guardian,
				childrenFinder.findChildren(List.of(guardian.getId())).getOrDefault(guardian.getId(), List.of()));
	}

	/**
	 * Escapes the {@code LIKE} wildcards and the escape character itself, and wraps the text in {@code %} so it
	 * matches anywhere in the name.
	 */
	static String likePattern(String text) {
		String escaped = text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
		return "%" + escaped + "%";
	}

}
