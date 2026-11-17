package com.manuelaalecio.educare_backend.child.application;

import java.text.Collator;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import com.manuelaalecio.educare_backend.child.domain.BirthCertificateAlreadyInUseException;
import com.manuelaalecio.educare_backend.child.domain.BirthDateInFutureException;
import com.manuelaalecio.educare_backend.child.domain.Child;
import com.manuelaalecio.educare_backend.child.domain.ChildClassroom;
import com.manuelaalecio.educare_backend.child.domain.ChildClassroomRepository;
import com.manuelaalecio.educare_backend.child.domain.ChildDetails;
import com.manuelaalecio.educare_backend.child.domain.ChildGuardian;
import com.manuelaalecio.educare_backend.child.domain.ChildGuardianRepository;
import com.manuelaalecio.educare_backend.child.domain.ChildNotFoundException;
import com.manuelaalecio.educare_backend.child.domain.ChildRepository;
import com.manuelaalecio.educare_backend.child.domain.ChildSpecifications;
import com.manuelaalecio.educare_backend.child.domain.ClassroomNotFoundForChildException;
import com.manuelaalecio.educare_backend.child.domain.CpfAlreadyInUseException;
import com.manuelaalecio.educare_backend.child.domain.GuardianNotFoundForChildException;
import com.manuelaalecio.educare_backend.child.domain.Relationship;
import com.manuelaalecio.educare_backend.child.domain.RgAlreadyInUseException;
import com.manuelaalecio.educare_backend.classroom.application.ClassroomLookup;
import com.manuelaalecio.educare_backend.classroom.application.ClassroomRef;
import com.manuelaalecio.educare_backend.guardian.application.GuardianContact;
import com.manuelaalecio.educare_backend.guardian.application.GuardianRegistry;
import com.manuelaalecio.educare_backend.shared.error.FieldConflictException;
import com.manuelaalecio.educare_backend.shared.error.InvalidFieldException;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Use cases of the children registry. Every child is in exactly one class: the creation opens the link and the
 * update moves the child when the class changes, keeping the previous links as history. The classes belong to the
 * {@code classroom} module and are read through {@link ClassroomLookup}.
 * <p>
 * Every child also has 1 to 5 guardians, each an existing guardian or a new one created with the child, always with
 * the relationship. The guardians belong to the {@code guardian} module and are created and read through
 * {@link GuardianRegistry}; the links (and their relationship) belong to this module. The child, the new guardians
 * and the links are saved in the same transaction, so any refusal undoes all of them.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class ChildService {

	/**
	 * The institution's time zone: "today" follows it, not the UTC application clock, so the date does not move
	 * forward in the evening.
	 */
	static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");

	/**
	 * The guardians of a child are shown in the alphabetical order of Portuguese, accents included.
	 */
	private static final Comparator<ChildGuardianView> GUARDIAN_ORDER = Comparator
		.comparing((ChildGuardianView view) -> view.contact().name(), Collator.getInstance(Locale.of("pt", "BR")))
		.thenComparing(view -> view.contact().id());

	private final ChildRepository childRepository;
	private final ChildClassroomRepository linkRepository;
	private final ClassroomLookup classroomLookup;
	private final ChildGuardianRepository guardianLinkRepository;
	private final GuardianRegistry guardianRegistry;
	private final Clock clock;

	/**
	 * Creates the child in the class, opening the link today, with its guardians: the new ones are created and every
	 * one is linked. Nothing is saved when the class or an existing guardian does not exist, or when a new guardian
	 * is refused.
	 */
	public ChildView create(ChildDetails details, UUID classroomId, List<ChildGuardianCommand> guardians) {
		Child child = validated(details);
		ClassroomRef classroom = existingClassroom(classroomId);
		checkDocumentsAreFree(child, null);
		ResolvedGuardians resolved = resolveGuardians(guardians);
		Child saved = saveAndFlush(child);
		openLink(saved.getId(), classroomId);
		guardianLinkRepository.saveAllAndFlush(resolved.relationships()
			.entrySet()
			.stream()
			.map(entry -> ChildGuardian.link(saved.getId(), entry.getKey(), entry.getValue()))
			.toList());
		return new ChildView(saved, classroom, resolved.views());
	}

	@Transactional(readOnly = true)
	public ChildView findById(UUID id) {
		Child child = load(id);
		List<ChildGuardian> links = guardianLinkRepository.findByChildId(id);
		Map<UUID, GuardianContact> contacts = guardianRegistry
			.findContacts(links.stream().map(ChildGuardian::getGuardianId).toList());
		List<ChildGuardianView> guardians = links.stream()
			.map(link -> new ChildGuardianView(requiredContact(contacts.get(link.getGuardianId())),
					link.getRelationship()))
			.sorted(GUARDIAN_ORDER)
			.toList();
		return new ChildView(child, currentClassroom(id), guardians);
	}

	/**
	 * A page of children, each with its current class: the page, the active links of its children and their classes
	 * are read in a fixed number of queries, whatever the page size.
	 *
	 * @param name part of the name, compared ignoring case and accents, with {@code %} and {@code _} taken
	 * literally; ignored when {@code null} or blank
	 * @param classroomId only the children currently in this class; ignored when {@code null}
	 */
	@Transactional(readOnly = true)
	public Page<ChildSummaryView> list(String name, UUID classroomId, Pageable pageable) {
		List<Specification<Child>> filters = new ArrayList<>();
		if (name != null && !name.isBlank()) {
			filters.add(ChildSpecifications.nameMatches(likePattern(name)));
		}
		if (classroomId != null) {
			filters.add(ChildSpecifications.inClassroom(classroomId));
		}
		Page<Child> page = childRepository.findAll(Specification.allOf(filters), pageable);
		if (page.isEmpty()) {
			return page.map(child -> new ChildSummaryView(child, null));
		}
		Map<UUID, UUID> classroomByChild = linkRepository.findActiveByChildIdIn(page.map(Child::getId).getContent())
			.stream()
			.collect(Collectors.toMap(ChildClassroom::getChildId, ChildClassroom::getClassroomId));
		Map<UUID, ClassroomRef> classrooms = classroomLookup
			.findAll(classroomByChild.values().stream().distinct().toList());
		return page.map(child -> {
			UUID current = classroomByChild.get(child.getId());
			return new ChildSummaryView(child, required(current == null ? null : classrooms.get(current)));
		});
	}

	/**
	 * Replaces every field of the child and, when the class changes, ends the current link today and opens another
	 * one. The child's row is locked first, so concurrent updates of the same child run one after the other and each
	 * sees the links left by the previous one. The documents are checked on a detached copy before the managed
	 * entity changes: the checks are queries, which flush pending changes, so a repeated document set beforehand
	 * would violate the constraint during the check instead of becoming a conflict.
	 * <p>
	 * The guardians received replace the current links: a guardian that stays keeps its link with the relationship
	 * received, a guardian left out is unlinked (and stays registered), and the others are linked. The data of an
	 * existing guardian never changes here.
	 */
	public ChildView update(UUID id, ChildDetails details, UUID classroomId, List<ChildGuardianCommand> guardians) {
		Child child = childRepository.findByIdForUpdate(id).orElseThrow(ChildNotFoundException::new);
		Child candidate = validated(details);
		ClassroomRef classroom = existingClassroom(classroomId);
		checkDocumentsAreFree(candidate, id);
		ResolvedGuardians resolved = resolveGuardians(guardians);
		child.update(details);
		Child saved = saveAndFlush(child);
		ChildClassroom current = activeLink(id);
		if (!current.getClassroomId().equals(classroomId)) {
			current.close(today());
			// flushed before the new link: Hibernate inserts before it updates, and the new active link would
			// otherwise violate child_classrooms_active_key while the current one is still open
			linkRepository.saveAndFlush(current);
			openLink(id, classroomId);
		}
		replaceGuardianLinks(id, resolved.relationships());
		return new ChildView(saved, classroom, resolved.views());
	}

	/**
	 * Every class the child has been in, the most recent first, with the current names of the classes.
	 */
	@Transactional(readOnly = true)
	public List<ChildClassroomView> history(UUID id) {
		if (!childRepository.existsById(id)) {
			throw new ChildNotFoundException();
		}
		List<ChildClassroom> links = linkRepository.findHistory(id);
		Map<UUID, ClassroomRef> classrooms = classroomLookup
			.findAll(links.stream().map(ChildClassroom::getClassroomId).distinct().toList());
		return links.stream()
			.map(link -> new ChildClassroomView(link, required(classrooms.get(link.getClassroomId()))))
			.toList();
	}

	/**
	 * Deletes the child for good, with its links to classes and to guardians ({@code ON DELETE CASCADE}); the
	 * guardians stay registered, and its CPF, RG and birth certificate number become free for another child. The links
	 * are not loaded, so no managed link outlives its row.
	 */
	public void delete(UUID id) {
		childRepository.delete(load(id));
	}

	/**
	 * @return today in the institution's time zone
	 */
	LocalDate today() {
		return LocalDate.now(clock.withZone(ZONE));
	}

	/**
	 * The guardians of the body, by id and in the order received, with the relationship of each one, and their views
	 * for the response.
	 */
	private record ResolvedGuardians(Map<UUID, Relationship> relationships,
			List<ChildGuardianView> views) {
	}

	/**
	 * Checks that the existing guardians exist (a single query) and then creates the new ones, in the order of the
	 * body. Errors point to the item: {@code guardians[i].guardianId} for a guardian that does not exist, and
	 * {@code guardians[i].guardian.<field>} for a new guardian refused by a rule that depends on the clock or on the
	 * database. Two new guardians with the same CPF: the second finds the first, already saved, and is refused.
	 */
	private ResolvedGuardians resolveGuardians(List<ChildGuardianCommand> guardians) {
		Map<UUID, GuardianContact> contacts = new HashMap<>(guardianRegistry.findContacts(
				guardians.stream().map(ChildGuardianCommand::guardianId).filter(Objects::nonNull).toList()));
		for (int i = 0; i < guardians.size(); i++) {
			UUID guardianId = guardians.get(i).guardianId();
			if (guardianId != null && !contacts.containsKey(guardianId)) {
				throw new GuardianNotFoundForChildException("guardians[" + i + "].guardianId");
			}
		}
		List<UUID> ids = new ArrayList<>();
		List<UUID> created = new ArrayList<>();
		for (int i = 0; i < guardians.size(); i++) {
			ChildGuardianCommand command = guardians.get(i);
			if (command.guardianId() != null) {
				ids.add(command.guardianId());
			}
			else {
				UUID newId = createGuardian(command, "guardians[" + i + "].guardian.");
				ids.add(newId);
				created.add(newId);
			}
		}
		if (!created.isEmpty()) {
			contacts.putAll(guardianRegistry.findContacts(created));
		}
		Map<UUID, Relationship> relationships = new LinkedHashMap<>();
		for (int i = 0; i < guardians.size(); i++) {
			relationships.put(ids.get(i), guardians.get(i).relationship());
		}
		List<ChildGuardianView> views = relationships.entrySet()
			.stream()
			.map(entry -> new ChildGuardianView(requiredContact(contacts.get(entry.getKey())), entry.getValue()))
			.sorted(GUARDIAN_ORDER)
			.toList();
		return new ResolvedGuardians(relationships, views);
	}

	private UUID createGuardian(ChildGuardianCommand command, String fieldPrefix) {
		try {
			return guardianRegistry.create(command.newGuardian());
		}
		catch (InvalidFieldException ex) {
			throw ex.withFieldPrefix(fieldPrefix);
		}
		catch (FieldConflictException ex) {
			throw ex.withFieldPrefix(fieldPrefix);
		}
	}

	/**
	 * Unlinks the guardians left out (in one statement, run right away, before the inserts), changes the relationship
	 * of the ones that stay and links the new ones.
	 */
	private void replaceGuardianLinks(UUID childId,
			Map<UUID, Relationship> relationships) {
		List<ChildGuardian> current = guardianLinkRepository.findByChildId(childId);
		List<ChildGuardian> removed = current.stream()
			.filter(link -> !relationships.containsKey(link.getGuardianId()))
			.toList();
		if (!removed.isEmpty()) {
			guardianLinkRepository.deleteAllInBatch(removed);
		}
		Map<UUID, ChildGuardian> kept = current.stream()
			.collect(Collectors.toMap(ChildGuardian::getGuardianId, link -> link));
		List<ChildGuardian> changed = new ArrayList<>();
		relationships.forEach((guardianId, relationship) -> {
			ChildGuardian link = kept.get(guardianId);
			if (link == null) {
				changed.add(ChildGuardian.link(childId, guardianId, relationship));
			}
			else {
				link.changeRelationship(relationship);
				changed.add(link);
			}
		});
		guardianLinkRepository.saveAllAndFlush(changed);
	}

	/**
	 * A link always points to an existing guardian (foreign key): a missing one is a bug, not a client error.
	 */
	private static GuardianContact requiredContact(GuardianContact contact) {
		if (contact == null) {
			throw new IllegalStateException("Vínculo da criança sem responsável correspondente");
		}
		return contact;
	}

	/**
	 * Checks the rules that depend on the clock and returns a new, not yet saved child with the normalized data.
	 */
	private Child validated(ChildDetails details) {
		if (details.birthDate() != null && details.birthDate().isAfter(today())) {
			throw new BirthDateInFutureException();
		}
		return new Child(details);
	}

	private ClassroomRef existingClassroom(UUID classroomId) {
		return classroomLookup.find(classroomId).orElseThrow(ClassroomNotFoundForChildException::new);
	}

	private ClassroomRef currentClassroom(UUID childId) {
		return required(classroomLookup.find(activeLink(childId).getClassroomId()).orElse(null));
	}

	private ChildClassroom activeLink(UUID childId) {
		return linkRepository.findByChildIdAndEndDateIsNull(childId)
			.orElseThrow(() -> new IllegalStateException("Criança sem turma ativa"));
	}

	/**
	 * A link always points to an existing class (foreign key), and every child has an active link: a missing one is
	 * a bug, not a client error.
	 */
	private static ClassroomRef required(ClassroomRef classroom) {
		if (classroom == null) {
			throw new IllegalStateException("Vínculo da criança sem turma correspondente");
		}
		return classroom;
	}

	/**
	 * Opens the link today. A class deleted after the check violates the link's foreign key here, which becomes the
	 * same {@code 400} as a class that does not exist.
	 */
	private void openLink(UUID childId, UUID classroomId) {
		try {
			linkRepository.saveAndFlush(ChildClassroom.open(childId, classroomId, today()));
		}
		catch (DataIntegrityViolationException ex) {
			if (ChildClassroomRepository.CLASSROOM_FOREIGN_KEY.equals(violatedConstraint(ex))) {
				throw new ClassroomNotFoundForChildException();
			}
			throw ex;
		}
	}

	/**
	 * Checks CPF, RG and birth certificate number, in this order, against the other children. A missing document is
	 * not checked: the derived queries would compare it with {@code IS NULL} and match every child without one.
	 *
	 * @param ownId the id of the child being updated, ignored in the checks; {@code null} on creation
	 */
	private void checkDocumentsAreFree(Child child, UUID ownId) {
		if (child.getCpf() != null && (ownId == null ? childRepository.existsByCpf(child.getCpf())
				: childRepository.existsByCpfAndIdNot(child.getCpf(), ownId))) {
			throw new CpfAlreadyInUseException();
		}
		if (child.getRg() != null && (ownId == null ? childRepository.existsByRg(child.getRg())
				: childRepository.existsByRgAndIdNot(child.getRg(), ownId))) {
			throw new RgAlreadyInUseException();
		}
		String number = child.getBirthCertificateNumber();
		if (number != null && (ownId == null ? childRepository.existsByBirthCertificateNumber(number)
				: childRepository.existsByBirthCertificateNumberAndIdNot(number, ownId))) {
			throw new BirthCertificateAlreadyInUseException();
		}
	}

	private Child load(UUID id) {
		return childRepository.findById(id).orElseThrow(ChildNotFoundException::new);
	}

	/**
	 * Flushes so a concurrent save of the same document violates its unique constraint here, where it can still
	 * become a conflict. Any other violation would be a bug, not a conflict, so it is rethrown.
	 */
	private Child saveAndFlush(Child child) {
		try {
			return childRepository.saveAndFlush(child);
		}
		catch (DataIntegrityViolationException ex) {
			String constraint = violatedConstraint(ex);
			if (ChildRepository.CPF_UNIQUE_CONSTRAINT.equals(constraint)) {
				throw new CpfAlreadyInUseException();
			}
			if (ChildRepository.RG_UNIQUE_CONSTRAINT.equals(constraint)) {
				throw new RgAlreadyInUseException();
			}
			if (ChildRepository.BIRTH_CERTIFICATE_NUMBER_UNIQUE_CONSTRAINT.equals(constraint)) {
				throw new BirthCertificateAlreadyInUseException();
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

	/**
	 * Strips the text, escapes the {@code LIKE} wildcards and the escape character itself, and wraps it in
	 * {@code %} so it matches anywhere in the name.
	 */
	static String likePattern(String name) {
		String escaped = name.strip().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
		return "%" + escaped + "%";
	}

}
