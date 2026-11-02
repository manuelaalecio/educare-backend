package com.manuelaalecio.educare_backend.classroom.application;

/**
 * How many active children a class has ("students" in the names, kept for compatibility). {@code male} and
 * {@code female} count only the children whose gender is {@code MALE} or {@code FEMALE}; children of another gender,
 * or without one, count only in {@code total}.
 */
public record StudentCounts(long total, long male, long female) {

	public static final StudentCounts ZERO = new StudentCounts(0, 0, 0);

	public StudentCounts {
		if (total < 0 || male < 0 || female < 0) {
			throw new IllegalArgumentException("Contagens de crianças não podem ser negativas");
		}
		if (male + female > total) {
			throw new IllegalArgumentException("Meninos e meninas não podem passar do total de crianças");
		}
	}

	public boolean hasStudents() {
		return total > 0;
	}

}
