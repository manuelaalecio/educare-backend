package com.manuelaalecio.educare_backend.classroom.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.manuelaalecio.educare_backend.classroom.domain.Classroom;
import com.manuelaalecio.educare_backend.classroom.domain.ClassroomRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ClassroomLookupTest {

	private static final UUID ID = UUID.fromString("0190f4a2-0000-7000-8000-000000000001");
	private static final UUID OTHER_ID = UUID.fromString("0190f4a2-0000-7000-8000-000000000002");
	private static final UUID MISSING_ID = UUID.fromString("0190f4a2-0000-7000-8000-000000000003");

	@Mock
	private ClassroomRepository classroomRepository;

	@InjectMocks
	private ClassroomLookup classroomLookup;

	@Test
	void shouldReturnIdAndNameWhenClassroomExists() {
		// given
		when(classroomRepository.findById(ID)).thenReturn(Optional.of(classroom(ID, "Turma 1")));

		// when / then
		assertThat(classroomLookup.find(ID)).contains(new ClassroomRef(ID, "Turma 1"));
	}

	@Test
	void shouldReturnEmptyWhenClassroomDoesNotExist() {
		// given
		when(classroomRepository.findById(MISSING_ID)).thenReturn(Optional.empty());

		// when / then
		assertThat(classroomLookup.find(MISSING_ID)).isEmpty();
	}

	@Test
	void shouldMapFoundClassroomsByIdWhenSomeIdsDoNotExist() {
		// given
		List<UUID> ids = List.of(ID, OTHER_ID, MISSING_ID);
		when(classroomRepository.findAllById(ids))
			.thenReturn(List.of(classroom(ID, "Turma 1"), classroom(OTHER_ID, "Turma 2")));

		// when
		Map<UUID, ClassroomRef> found = classroomLookup.findAll(ids);

		// then
		assertThat(found).containsOnly(Map.entry(ID, new ClassroomRef(ID, "Turma 1")),
				Map.entry(OTHER_ID, new ClassroomRef(OTHER_ID, "Turma 2")));
	}

	@Test
	void shouldNotQueryWhenNoIdIsGiven() {
		// when / then
		assertThat(classroomLookup.findAll(List.of())).isEmpty();
		verifyNoInteractions(classroomRepository);
	}

	private static Classroom classroom(UUID id, String name) {
		var classroom = new Classroom(name);
		ReflectionTestUtils.setField(classroom, "id", id);
		return classroom;
	}

}
