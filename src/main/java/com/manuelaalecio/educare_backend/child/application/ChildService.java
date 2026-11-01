package com.manuelaalecio.educare_backend.child.application;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import com.manuelaalecio.educare_backend.child.domain.BirthCertificateAlreadyInUseException;
import com.manuelaalecio.educare_backend.child.domain.BirthDateInFutureException;
import com.manuelaalecio.educare_backend.child.domain.Child;
import com.manuelaalecio.educare_backend.child.domain.ChildDetails;
import com.manuelaalecio.educare_backend.child.domain.ChildNotFoundException;
import com.manuelaalecio.educare_backend.child.domain.ChildRepository;
import com.manuelaalecio.educare_backend.child.domain.CpfAlreadyInUseException;
import com.manuelaalecio.educare_backend.child.domain.RgAlreadyInUseException;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Use cases of the children registry.
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
	private final Clock clock;

	public Child create(ChildDetails details) {
		Child child = validated(details);
		checkDocumentsAreFree(child, null);
		return saveAndFlush(child);
	}

	@Transactional(readOnly = true)
	public Child findById(UUID id) {
		return load(id);
	}

	/**
	 * @param name part of the name, compared ignoring case and accents, with {@code %} and {@code _} taken
	 * literally; ignored when {@code null} or blank
	 */
	@Transactional(readOnly = true)
	public Page<Child> list(String name, Pageable pageable) {
		if (name == null || name.isBlank()) {
			return childRepository.findAll(pageable);
		}
		return childRepository.searchByName(likePattern(name), pageable);
	}

	/**
	 * Replaces every field of the child. The documents are checked on a detached copy before the managed entity
	 * changes: the checks are queries, which flush pending changes, so a repeated document set beforehand would
	 * violate the constraint during the check instead of becoming a conflict.
	 */
	public Child update(UUID id, ChildDetails details) {
		Child child = load(id);
		checkDocumentsAreFree(validated(details), id);
		child.update(details);
		return saveAndFlush(child);
	}

	/**
	 * Deletes the child for good; its CPF, RG and birth certificate number become free for another child.
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
