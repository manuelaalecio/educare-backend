package com.manuelaalecio.educare_backend.child.application;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import com.manuelaalecio.educare_backend.child.domain.BirthCertificateAlreadyInUseException;
import com.manuelaalecio.educare_backend.child.domain.BirthDateInFutureException;
import com.manuelaalecio.educare_backend.child.domain.Child;
import com.manuelaalecio.educare_backend.child.domain.ChildClassroom;
import com.manuelaalecio.educare_backend.child.domain.ChildClassroomRepository;
import com.manuelaalecio.educare_backend.child.domain.ChildDetails;
import com.manuelaalecio.educare_backend.child.domain.ChildNotFoundException;
import com.manuelaalecio.educare_backend.child.domain.ChildRepository;
import com.manuelaalecio.educare_backend.child.domain.ChildSpecifications;
import com.manuelaalecio.educare_backend.child.domain.ClassroomNotFoundForChildException;
import com.manuelaalecio.educare_backend.child.domain.CpfAlreadyInUseException;
import com.manuelaalecio.educare_backend.child.domain.RgAlreadyInUseException;
import com.manuelaalecio.educare_backend.classroom.application.ClassroomLookup;
import com.manuelaalecio.educare_backend.classroom.application.ClassroomRef;
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

	private final ChildRepository childRepository;
	private final ChildClassroomRepository linkRepository;
	private final ClassroomLookup classroomLookup;
	private final Clock clock;

	/**
	 * Creates the child in the class, opening the link today. Nothing is saved when the class does not exist.
	 */
	public ChildView create(ChildDetails details, UUID classroomId) {
		Child child = validated(details);
		ClassroomRef classroom = existingClassroom(classroomId);
		checkDocumentsAreFree(child, null);
		Child saved = saveAndFlush(child);
		openLink(saved.getId(), classroomId);
		return new ChildView(saved, classroom);
	}

	@Transactional(readOnly = true)
	public ChildView findById(UUID id) {
		Child child = load(id);
		return new ChildView(child, currentClassroom(id));
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
	 */
	public ChildView update(UUID id, ChildDetails details, UUID classroomId) {
		Child child = childRepository.findByIdForUpdate(id).orElseThrow(ChildNotFoundException::new);
		Child candidate = validated(details);
		ClassroomRef classroom = existingClassroom(classroomId);
		checkDocumentsAreFree(candidate, id);
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
		return new ChildView(saved, classroom);
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
	 * Deletes the child for good, with its links ({@code ON DELETE CASCADE}); its CPF, RG and birth certificate
	 * number become free for another child. The links are not loaded, so no managed link outlives its row.
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
