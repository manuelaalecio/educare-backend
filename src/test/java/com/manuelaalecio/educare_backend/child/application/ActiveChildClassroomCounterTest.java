package com.manuelaalecio.educare_backend.child.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.manuelaalecio.educare_backend.child.domain.ChildClassroomRepository;
import com.manuelaalecio.educare_backend.child.domain.ClassroomStudentCountRow;
import com.manuelaalecio.educare_backend.classroom.application.StudentCounts;
import org.junit.jupiter.api.Test;

class ActiveChildClassroomCounterTest {

	private static final UUID TURMA_1 = UUID.fromString("0190f4a2-0000-7000-8000-0000000000c1");
	private static final UUID TURMA_2 = UUID.fromString("0190f4a2-0000-7000-8000-0000000000c2");

	private final ChildClassroomRepository linkRepository = mock(ChildClassroomRepository.class);

	private final ActiveChildClassroomCounter counter = new ActiveChildClassroomCounter(linkRepository);

	@Test
	void shouldConvertRowsToCountsWhenCountingSeveralClassrooms() {
		// given
		when(linkRepository.countActiveByClassroomIdIn(List.of(TURMA_1, TURMA_2)))
			.thenReturn(List.of(row(TURMA_1, 4, 2, 1)));

		// when
		Map<UUID, StudentCounts> counts = counter.countFor(List.of(TURMA_1, TURMA_2));

		// then
		assertThat(counts).containsOnly(Map.entry(TURMA_1, new StudentCounts(4, 2, 1)));
	}

	@Test
	void shouldNotQueryWhenNoClassroomIsGiven() {
		// when / then
		assertThat(counter.countFor(List.of())).isEmpty();
		verifyNoInteractions(linkRepository);
	}

	@Test
	void shouldReturnCountsOfOneClassroomWhenItHasChildren() {
		// given
		when(linkRepository.countActiveByClassroomIdIn(List.of(TURMA_1))).thenReturn(List.of(row(TURMA_1, 3, 1, 1)));

		// when / then
		assertThat(counter.countFor(TURMA_1)).isEqualTo(new StudentCounts(3, 1, 1));
	}

	@Test
	void shouldReturnZeroWhenClassroomHasNoActiveChildren() {
		// given
		when(linkRepository.countActiveByClassroomIdIn(List.of(TURMA_1))).thenReturn(List.of());

		// when / then
		assertThat(counter.countFor(TURMA_1)).isEqualTo(StudentCounts.ZERO);
	}

	@Test
	void shouldTellWhetherClassroomHasHistory() {
		// given
		when(linkRepository.existsByClassroomIdAndEndDateIsNotNull(TURMA_1)).thenReturn(true);
		when(linkRepository.existsByClassroomIdAndEndDateIsNotNull(TURMA_2)).thenReturn(false);

		// when / then
		assertThat(counter.hasPastStudents(TURMA_1)).isTrue();
		assertThat(counter.hasPastStudents(TURMA_2)).isFalse();
	}

	private static ClassroomStudentCountRow row(UUID classroomId, long total, long male, long female) {
		return new ClassroomStudentCountRow() {

			@Override
			public UUID getClassroomId() {
				return classroomId;
			}

			@Override
			public long getTotal() {
				return total;
			}

			@Override
			public long getMale() {
				return male;
			}

			@Override
			public long getFemale() {
				return female;
			}

		};
	}

}
