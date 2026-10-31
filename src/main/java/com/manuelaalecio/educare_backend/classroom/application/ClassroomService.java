package com.manuelaalecio.educare_backend.classroom.application;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.manuelaalecio.educare_backend.classroom.domain.Classroom;
import com.manuelaalecio.educare_backend.classroom.domain.ClassroomHasStudentsException;
import com.manuelaalecio.educare_backend.classroom.domain.ClassroomNameAlreadyInUseException;
import com.manuelaalecio.educare_backend.classroom.domain.ClassroomNotFoundException;
import com.manuelaalecio.educare_backend.classroom.domain.ClassroomRepository;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Use cases of the class registry. Every class is returned with its student counts, provided by the
 * {@link ClassroomStudentCounter}.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class ClassroomService {

	private final ClassroomRepository classroomRepository;
	private final ClassroomStudentCounter studentCounter;

	public ClassroomOverview create(String name) {
		Classroom classroom = new Classroom(name);
		if (classroomRepository.existsByNormalizedName(classroom.getName())) {
			throw new ClassroomNameAlreadyInUseException();
		}
		return withCounts(saveAndFlush(classroom));
	}

	/**
	 * @return every class, in alphabetical order ignoring case and accents
	 */
	@Transactional(readOnly = true)
	public List<ClassroomOverview> list() {
		List<Classroom> classrooms = classroomRepository.findAllOrderedByName();
		Map<UUID, StudentCounts> counts = studentCounter.countFor(classrooms.stream().map(Classroom::getId).toList());
		return classrooms.stream()
				.map(classroom -> new ClassroomOverview(classroom,
						counts.getOrDefault(classroom.getId(), StudentCounts.ZERO)))
				.toList();
	}

	@Transactional(readOnly = true)
	public ClassroomOverview findById(UUID id) {
		return withCounts(load(id));
	}

	/**
	 * Accepts a variation of the class's own name (e.g. only changing case). The name is checked before the managed
	 * entity changes: the check is a native query, which flushes pending changes, so a repeated name set beforehand
	 * would violate the index during the check instead of becoming a conflict.
	 */
	public ClassroomOverview rename(UUID id, String name) {
		Classroom classroom = load(id);
		String newName = Classroom.normalizeName(name);
		if (classroomRepository.existsByNormalizedNameAndIdNot(newName, id)) {
			throw new ClassroomNameAlreadyInUseException();
		}
		classroom.rename(newName);
		return withCounts(saveAndFlush(classroom));
	}

	/**
	 * Deletes the class for good, unless it has students.
	 */
	public void delete(UUID id) {
		Classroom classroom = load(id);
		if (studentCounter.countFor(id).hasStudents()) {
			throw new ClassroomHasStudentsException();
		}
		classroomRepository.delete(classroom);
	}

	private Classroom load(UUID id) {
		return classroomRepository.findById(id).orElseThrow(ClassroomNotFoundException::new);
	}

	private ClassroomOverview withCounts(Classroom classroom) {
		return new ClassroomOverview(classroom, studentCounter.countFor(classroom.getId()));
	}

	/**
	 * Flushes so a concurrent save of the same name violates {@code classrooms_name_key} here, where it can still
	 * become a conflict. Any other violation would be a bug, not a conflict, so it is rethrown.
	 */
	private Classroom saveAndFlush(Classroom classroom) {
		try {
			return classroomRepository.saveAndFlush(classroom);
		}
		catch (DataIntegrityViolationException ex) {
			if (violatesNameUniqueness(ex)) {
				throw new ClassroomNameAlreadyInUseException();
			}
			throw ex;
		}
	}

	private static boolean violatesNameUniqueness(Throwable ex) {
		for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
			if (cause instanceof ConstraintViolationException violation) {
				return ClassroomRepository.NAME_UNIQUE_CONSTRAINT.equals(violation.getConstraintName());
			}
		}
		return false;
	}

}
