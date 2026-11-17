package com.manuelaalecio.educare_backend.child.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.manuelaalecio.educare_backend.child.domain.ChildGuardianRepository;
import com.manuelaalecio.educare_backend.child.domain.GuardianChildRow;
import com.manuelaalecio.educare_backend.child.domain.Relationship;
import com.manuelaalecio.educare_backend.guardian.application.GuardianChildRef;
import org.junit.jupiter.api.Test;

class LinkedGuardianChildrenFinderTest {

	private static final UUID MARIA = UUID.fromString("0190f4a2-0000-7000-8000-0000000000d1");
	private static final UUID JOAO = UUID.fromString("0190f4a2-0000-7000-8000-0000000000d2");
	private static final UUID CARLA = UUID.fromString("0190f4a2-0000-7000-8000-0000000000d3");
	private static final UUID ANA = UUID.fromString("0190f4a2-0000-7000-8000-0000000000c1");
	private static final UUID PEDRO = UUID.fromString("0190f4a2-0000-7000-8000-0000000000c2");

	private final ChildGuardianRepository linkRepository = mock(ChildGuardianRepository.class);

	private final LinkedGuardianChildrenFinder finder = new LinkedGuardianChildrenFinder(linkRepository);

	@Test
	void shouldGroupChildrenByGuardianKeepingOrderWhenSearchingSeveralGuardians() {
		// given
		List<GuardianChildRow> rows = List.of(row(MARIA, ANA, "Ana Souza", Relationship.MOTHER),
				row(JOAO, ANA, "Ana Souza", Relationship.FATHER),
				row(MARIA, PEDRO, "Pedro Souza", Relationship.STEPMOTHER));
		when(linkRepository.findChildrenOfGuardians(List.of(MARIA, JOAO, CARLA))).thenReturn(rows);

		// when
		Map<UUID, List<GuardianChildRef>> children = finder.findChildren(List.of(MARIA, JOAO, CARLA));

		// then
		assertThat(children).containsOnlyKeys(MARIA, JOAO);
		assertThat(children.get(MARIA)).containsExactly(new GuardianChildRef(ANA, "Ana Souza", "MOTHER"),
				new GuardianChildRef(PEDRO, "Pedro Souza", "STEPMOTHER"));
		assertThat(children.get(JOAO)).containsExactly(new GuardianChildRef(ANA, "Ana Souza", "FATHER"));
	}

	@Test
	void shouldNotQueryWhenNoGuardianIsGiven() {
		// when / then
		assertThat(finder.findChildren(List.of())).isEmpty();
		verifyNoInteractions(linkRepository);
	}

	private static GuardianChildRow row(UUID guardianId, UUID childId, String childName, Relationship relationship) {
		GuardianChildRow row = mock(GuardianChildRow.class);
		when(row.getGuardianId()).thenReturn(guardianId);
		when(row.getChildId()).thenReturn(childId);
		when(row.getChildName()).thenReturn(childName);
		when(row.getRelationship()).thenReturn(relationship);
		return row;
	}

}
