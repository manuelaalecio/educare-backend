package com.manuelaalecio.educare_backend.child.api.dto;

import java.time.LocalDate;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * A period of the child in a class, with the current name of the class. {@code endDate} is {@code null} for the
 * current class.
 */
public record ChildClassroomResponse(UUID classroomId, String classroomName, LocalDate startDate,
		@Nullable LocalDate endDate) {
}
