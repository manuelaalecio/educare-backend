package com.manuelaalecio.educare_backend.classroom.application;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * Counts the students of the classes. The module that links children to classes (enrollment) implements it, so
 * {@code classroom} never depends on that module; until it exists, {@link NoEnrollmentStudentCounter} is the only
 * implementation.
 */
public interface ClassroomStudentCounter {

	StudentCounts countFor(UUID classroomId);

	/**
	 * Counts several classes at once, in a single query in a real implementation.
	 *
	 * @return the counts by class id; a class missing from the map has {@link StudentCounts#ZERO}
	 */
	Map<UUID, StudentCounts> countFor(Collection<UUID> classroomIds);

}
