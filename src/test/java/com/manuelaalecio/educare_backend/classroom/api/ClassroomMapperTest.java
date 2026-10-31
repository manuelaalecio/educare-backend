package com.manuelaalecio.educare_backend.classroom.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import com.manuelaalecio.educare_backend.classroom.api.dto.ClassroomResponse;
import com.manuelaalecio.educare_backend.classroom.application.ClassroomOverview;
import com.manuelaalecio.educare_backend.classroom.application.StudentCounts;
import com.manuelaalecio.educare_backend.classroom.domain.Classroom;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ClassroomMapperTest {

	private static final UUID ID = UUID.fromString("0190f4a2-0000-7000-8000-000000000001");
	private static final Instant CREATED_AT = Instant.parse("2026-11-03T10:00:00Z");
	private static final Instant UPDATED_AT = Instant.parse("2026-11-04T09:00:00Z");

	private final ClassroomMapper mapper = new ClassroomMapper();

	@Test
	void shouldMapEveryFieldWhenConvertingToResponse() {
		// given
		var classroom = new Classroom("Maternal A");
		ReflectionTestUtils.setField(classroom, "id", ID);
		ReflectionTestUtils.setField(classroom, "createdAt", CREATED_AT);
		ReflectionTestUtils.setField(classroom, "updatedAt", UPDATED_AT);

		// when
		ClassroomResponse response = mapper.toResponse(
				new ClassroomOverview(classroom, new StudentCounts(3, 1, 1)));

		// then
		assertThat(response).isEqualTo(new ClassroomResponse(ID, "Maternal A", 3, 1, 1, CREATED_AT, UPDATED_AT));
	}

}
