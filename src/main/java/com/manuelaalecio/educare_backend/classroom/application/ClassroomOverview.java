package com.manuelaalecio.educare_backend.classroom.application;

import com.manuelaalecio.educare_backend.classroom.domain.Classroom;

/**
 * A class together with its student counts, as every use case of {@link ClassroomService} returns it.
 */
public record ClassroomOverview(Classroom classroom, StudentCounts students) {
}
