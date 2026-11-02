package com.manuelaalecio.educare_backend.classroom.application;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * Counts the children of the classes ("students" in the names, kept for compatibility). The {@code child} module,
 * which owns the links between children and classes, implements it, so {@code classroom} never depends on that
 * module. A child counts while its link to the class is active.
 */
public interface ClassroomStudentCounter {

	StudentCounts countFor(UUID classroomId);

	/**
	 * Counts several classes at once, in a single query in a real implementation.
	 *
	 * @return the counts by class id; a class missing from the map has {@link StudentCounts#ZERO}
	 */
	Map<UUID, StudentCounts> countFor(Collection<UUID> classroomIds);

	/**
	 * @return whether some child has already left the class (it has history), regardless of the active children
	 */
	boolean hasPastStudents(UUID classroomId);

}
