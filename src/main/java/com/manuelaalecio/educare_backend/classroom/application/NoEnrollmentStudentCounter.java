package com.manuelaalecio.educare_backend.classroom.application;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;

/**
 * Provisional {@link ClassroomStudentCounter}: children are not linked to classes yet, so every class has no
 * students. The change that adds enrollment replaces it with the real implementation and removes this class.
 */
@Component
public class NoEnrollmentStudentCounter implements ClassroomStudentCounter {

	@Override
	public StudentCounts countFor(UUID classroomId) {
		return StudentCounts.ZERO;
	}

	@Override
	public Map<UUID, StudentCounts> countFor(Collection<UUID> classroomIds) {
		return Map.of();
	}

}
