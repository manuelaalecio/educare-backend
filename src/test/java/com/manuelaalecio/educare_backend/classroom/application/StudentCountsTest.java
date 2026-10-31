package com.manuelaalecio.educare_backend.classroom.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class StudentCountsTest {

	@Test
	void shouldHaveNoStudentsWhenZero() {
		assertThat(StudentCounts.ZERO.hasStudents()).isFalse();
		assertThat(StudentCounts.ZERO).isEqualTo(new StudentCounts(0, 0, 0));
	}

	@Test
	void shouldHaveStudentsWhenTotalIsPositive() {
		// given a student of another gender, who counts only in the total
		var counts = new StudentCounts(1, 0, 0);

		// then
		assertThat(counts.hasStudents()).isTrue();
	}

	@ParameterizedTest
	@CsvSource({"-1, 0, 0", "1, -1, 0", "1, 0, -1", "1, 1, 1", "2, 3, 0"})
	void shouldRejectCountsWhenNegativeOrAboveTotal(long total, long male, long female) {
		assertThatIllegalArgumentException().isThrownBy(() -> new StudentCounts(total, male, female));
	}

}
