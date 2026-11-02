package com.manuelaalecio.educare_backend.child.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.LocalDate;
import java.util.UUID;

import com.manuelaalecio.educare_backend.shared.error.InvalidFieldException;
import org.junit.jupiter.api.Test;

class ChildClassroomTest {

	private static final UUID CHILD_ID = UUID.fromString("0190f4a2-0000-7000-8000-000000000001");
	private static final UUID CLASSROOM_ID = UUID.fromString("0190f4a2-0000-7000-8000-000000000002");
	private static final LocalDate OCTOBER_1ST = LocalDate.of(2026, 10, 1);
	private static final LocalDate NOVEMBER_1ST = LocalDate.of(2026, 11, 1);

	@Test
	void shouldOpenActiveLinkWithoutEndDate() {
		// when
		ChildClassroom link = ChildClassroom.open(CHILD_ID, CLASSROOM_ID, OCTOBER_1ST);

		// then
		assertThat(link.getChildId()).isEqualTo(CHILD_ID);
		assertThat(link.getClassroomId()).isEqualTo(CLASSROOM_ID);
		assertThat(link.getStartDate()).isEqualTo(OCTOBER_1ST);
		assertThat(link.getEndDate()).isNull();
		assertThat(link.isActive()).isTrue();
	}

	@Test
	void shouldRejectOpeningWhenAnArgumentIsMissing() {
		assertThatIllegalArgumentException().isThrownBy(() -> ChildClassroom.open(null, CLASSROOM_ID, OCTOBER_1ST));
		assertThatIllegalArgumentException().isThrownBy(() -> ChildClassroom.open(CHILD_ID, null, OCTOBER_1ST));
		assertThatIllegalArgumentException().isThrownBy(() -> ChildClassroom.open(CHILD_ID, CLASSROOM_ID, null));
	}

	@Test
	void shouldEndLinkWhenClosedOnLaterDay() {
		// given
		ChildClassroom link = ChildClassroom.open(CHILD_ID, CLASSROOM_ID, OCTOBER_1ST);

		// when
		link.close(NOVEMBER_1ST);

		// then
		assertThat(link.getEndDate()).isEqualTo(NOVEMBER_1ST);
		assertThat(link.isActive()).isFalse();
	}

	@Test
	void shouldEndLinkOnStartDayWhenClosedOnSameDay() {
		// given
		ChildClassroom link = ChildClassroom.open(CHILD_ID, CLASSROOM_ID, NOVEMBER_1ST);

		// when
		link.close(NOVEMBER_1ST);

		// then
		assertThat(link.getEndDate()).isEqualTo(link.getStartDate());
	}

	@Test
	void shouldRejectClosingBeforeStartDate() {
		// given
		ChildClassroom link = ChildClassroom.open(CHILD_ID, CLASSROOM_ID, NOVEMBER_1ST);

		// when / then
		assertThatIllegalArgumentException().isThrownBy(() -> link.close(OCTOBER_1ST));
		assertThat(link.isActive()).isTrue();
	}

	@Test
	void shouldRejectClosingLinkAlreadyClosed() {
		// given
		ChildClassroom link = ChildClassroom.open(CHILD_ID, CLASSROOM_ID, OCTOBER_1ST);
		link.close(NOVEMBER_1ST);

		// when / then
		assertThatIllegalArgumentException().isThrownBy(() -> link.close(NOVEMBER_1ST.plusDays(1)));
		assertThat(link.getEndDate()).isEqualTo(NOVEMBER_1ST);
	}

	@Test
	void shouldRejectClosingWithoutDate() {
		ChildClassroom link = ChildClassroom.open(CHILD_ID, CLASSROOM_ID, OCTOBER_1ST);

		assertThatIllegalArgumentException().isThrownBy(() -> link.close(null));
	}

	@Test
	void shouldPointToClassroomFieldWhenClassroomIsNotFound() {
		assertThat(new ClassroomNotFoundForChildException()).isInstanceOf(InvalidFieldException.class)
			.hasMessage("Turma não encontrada")
			.extracting(InvalidFieldException::getField)
			.isEqualTo("classroomId");
	}

}
