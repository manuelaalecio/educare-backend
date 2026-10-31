package com.manuelaalecio.educare_backend.classroom.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class NoEnrollmentStudentCounterTest {

	private static final UUID ID = UUID.fromString("0190f4a2-0000-7000-8000-000000000001");

	private final NoEnrollmentStudentCounter counter = new NoEnrollmentStudentCounter();

	@Test
	void shouldCountZeroWhenCountingOneClassroom() {
		// when
		StudentCounts counts = counter.countFor(ID);

		// then
		assertThat(counts).isEqualTo(StudentCounts.ZERO);
	}

	@Test
	void shouldReturnNoCountsWhenCountingSeveralClassrooms() {
		// when
		Map<UUID, StudentCounts> counts = counter.countFor(List.of(ID, UUID.randomUUID()));

		// then
		assertThat(counts).isEmpty();
	}

	@Test
	void shouldReturnNoCountsWhenCountingNoClassroom() {
		// when
		Map<UUID, StudentCounts> counts = counter.countFor(List.of());

		// then
		assertThat(counts).isEmpty();
	}

}
