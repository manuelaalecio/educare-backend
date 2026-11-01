package com.manuelaalecio.educare_backend.child.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ChildClassroomRepository extends JpaRepository<ChildClassroom, UUID> {

	/**
	 * Foreign key from the link to the class, reported by the database when the class is deleted while a link to it
	 * is saved.
	 */
	String CLASSROOM_FOREIGN_KEY = "child_classrooms_classroom_id_fkey";

	/**
	 * The current class of the child; every child has exactly one (see {@code child_classrooms_active_key}).
	 */
	Optional<ChildClassroom> findByChildIdAndEndDateIsNull(UUID childId);

	/**
	 * The active links of several children, in a single query.
	 */
	@Query("select l from ChildClassroom l where l.childId in :childIds and l.endDate is null")
	List<ChildClassroom> findActiveByChildIdIn(Collection<UUID> childIds);

	/**
	 * Every link of the child, the most recent first: by start date, then the active one before the ended ones of
	 * the same day, then by end date, and the last created first for repeated changes on the same day.
	 */
	@Query("""
			select l from ChildClassroom l where l.childId = :childId
			order by l.startDate desc, l.endDate desc nulls first, l.createdAt desc
			""")
	List<ChildClassroom> findHistory(UUID childId);

	/**
	 * Whether some child has already left the class (an ended link).
	 */
	boolean existsByClassroomIdAndEndDateIsNotNull(UUID classroomId);

	/**
	 * The counts of the active children of each class, in a single query. Classes without active children are
	 * missing from the result.
	 */
	@Query(value = """
			SELECT cc.classroom_id AS classroomId,
			       count(*) AS total,
			       count(*) FILTER (WHERE c.gender = 'MALE') AS male,
			       count(*) FILTER (WHERE c.gender = 'FEMALE') AS female
			FROM child_classrooms cc
			JOIN children c ON c.id = cc.child_id
			WHERE cc.end_date IS NULL AND cc.classroom_id IN (:classroomIds)
			GROUP BY cc.classroom_id
			""", nativeQuery = true)
	List<ClassroomStudentCountRow> countActiveByClassroomIdIn(Collection<UUID> classroomIds);

}
