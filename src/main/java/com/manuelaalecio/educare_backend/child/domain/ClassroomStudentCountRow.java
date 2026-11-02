package com.manuelaalecio.educare_backend.child.domain;

import java.util.UUID;

/**
 * Projection of the student counts of one class: active children in total, of gender {@code MALE} and of gender
 * {@code FEMALE}.
 */
public interface ClassroomStudentCountRow {

	UUID getClassroomId();

	long getTotal();

	long getMale();

	long getFemale();

}
