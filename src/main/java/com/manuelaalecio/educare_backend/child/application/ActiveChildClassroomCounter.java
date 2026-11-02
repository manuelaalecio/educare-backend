package com.manuelaalecio.educare_backend.child.application;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import com.manuelaalecio.educare_backend.child.domain.ChildClassroomRepository;
import com.manuelaalecio.educare_backend.child.domain.ClassroomStudentCountRow;
import com.manuelaalecio.educare_backend.classroom.application.ClassroomStudentCounter;
import com.manuelaalecio.educare_backend.classroom.application.StudentCounts;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The counts of the classes from the active links of the children: total, {@code MALE} and {@code FEMALE}. Lives
 * in {@code child}, which owns the links, and implements the port declared by {@code classroom}.
 */
@Component
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ActiveChildClassroomCounter implements ClassroomStudentCounter {

	private final ChildClassroomRepository linkRepository;

	@Override
	public StudentCounts countFor(UUID classroomId) {
		return countFor(List.of(classroomId)).getOrDefault(classroomId, StudentCounts.ZERO);
	}

	/**
	 * Every class in a single query; classes without active children are missing from the map.
	 */
	@Override
	public Map<UUID, StudentCounts> countFor(Collection<UUID> classroomIds) {
		if (classroomIds.isEmpty()) {
			return Map.of();
		}
		return linkRepository.countActiveByClassroomIdIn(classroomIds)
			.stream()
			.collect(Collectors.toMap(ClassroomStudentCountRow::getClassroomId,
					row -> new StudentCounts(row.getTotal(), row.getMale(), row.getFemale())));
	}

	@Override
	public boolean hasPastStudents(UUID classroomId) {
		return linkRepository.existsByClassroomIdAndEndDateIsNotNull(classroomId);
	}

}
