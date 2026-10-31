package com.manuelaalecio.educare_backend.classroom.application;

/**
 * How many students a class has. {@code male} and {@code female} count only the students whose gender is
 * {@code MALE} or {@code FEMALE}; students of another gender, or without one, count only in {@code total}.
 */
public record StudentCounts(long total, long male, long female) {

	public static final StudentCounts ZERO = new StudentCounts(0, 0, 0);

	public StudentCounts {
		if (total < 0 || male < 0 || female < 0) {
			throw new IllegalArgumentException("Contagens de alunos não podem ser negativas");
		}
		if (male + female > total) {
			throw new IllegalArgumentException("Meninos e meninas não podem passar do total de alunos");
		}
	}

	public boolean hasStudents() {
		return total > 0;
	}

}
