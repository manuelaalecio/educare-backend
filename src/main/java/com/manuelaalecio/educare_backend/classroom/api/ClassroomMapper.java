package com.manuelaalecio.educare_backend.classroom.api;

import com.manuelaalecio.educare_backend.classroom.api.dto.ClassroomResponse;
import com.manuelaalecio.educare_backend.classroom.application.ClassroomOverview;
import com.manuelaalecio.educare_backend.classroom.application.StudentCounts;
import com.manuelaalecio.educare_backend.classroom.domain.Classroom;
import org.springframework.stereotype.Component;

/**
 * Converts a class and its student counts into the API representation, in plain Java.
 */
@Component
public class ClassroomMapper {

	public ClassroomResponse toResponse(ClassroomOverview overview) {
		Classroom classroom = overview.classroom();
		StudentCounts students = overview.students();
		return new ClassroomResponse(classroom.getId(), classroom.getName(), students.total(), students.male(),
				students.female(), classroom.getCreatedAt(), classroom.getUpdatedAt());
	}

}
