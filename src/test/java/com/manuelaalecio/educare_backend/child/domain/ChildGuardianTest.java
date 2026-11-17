package com.manuelaalecio.educare_backend.child.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.UUID;

import com.manuelaalecio.educare_backend.shared.error.InvalidFieldException;
import org.junit.jupiter.api.Test;

class ChildGuardianTest {

	private static final UUID CHILD_ID = UUID.fromString("0190f4a2-0000-7000-8000-0000000000c1");
	private static final UUID GUARDIAN_ID = UUID.fromString("0190f4a2-0000-7000-8000-0000000000d1");

	@Test
	void shouldKeepChildGuardianAndRelationshipWhenLinked() {
		// when
		ChildGuardian link = ChildGuardian.link(CHILD_ID, GUARDIAN_ID, Relationship.MOTHER);

		// then
		assertThat(link).extracting(ChildGuardian::getChildId, ChildGuardian::getGuardianId,
				ChildGuardian::getRelationship)
			.containsExactly(CHILD_ID, GUARDIAN_ID, Relationship.MOTHER);
	}

	@Test
	void shouldRejectLinkWhenChildIsNull() {
		assertThatIllegalArgumentException().isThrownBy(() -> ChildGuardian.link(null, GUARDIAN_ID, Relationship.MOTHER))
			.withMessage("Criança, responsável e parentesco do vínculo são obrigatórios");
	}

	@Test
	void shouldRejectLinkWhenGuardianIsNull() {
		assertThatIllegalArgumentException().isThrownBy(() -> ChildGuardian.link(CHILD_ID, null, Relationship.MOTHER));
	}

	@Test
	void shouldRejectLinkWhenRelationshipIsNull() {
		assertThatIllegalArgumentException().isThrownBy(() -> ChildGuardian.link(CHILD_ID, GUARDIAN_ID, null));
	}

	@Test
	void shouldChangeRelationshipWhenAnotherIsGiven() {
		// given
		ChildGuardian link = ChildGuardian.link(CHILD_ID, GUARDIAN_ID, Relationship.OTHER);

		// when
		link.changeRelationship(Relationship.STEPMOTHER);

		// then
		assertThat(link.getRelationship()).isEqualTo(Relationship.STEPMOTHER);
	}

	@Test
	void shouldRejectRelationshipChangeWhenRelationshipIsNull() {
		// given
		ChildGuardian link = ChildGuardian.link(CHILD_ID, GUARDIAN_ID, Relationship.OTHER);

		// when / then
		assertThatIllegalArgumentException().isThrownBy(() -> link.changeRelationship(null))
			.withMessage("Parentesco do vínculo é obrigatório");
		assertThat(link.getRelationship()).isEqualTo(Relationship.OTHER);
	}

	@Test
	void shouldPointToItemFieldWhenGuardianIsNotFound() {
		// when
		var exception = new GuardianNotFoundForChildException("guardians[2].guardianId");

		// then
		assertThat(exception).isInstanceOf(InvalidFieldException.class).hasMessage("Responsável não encontrado");
		assertThat(exception.getField()).isEqualTo("guardians[2].guardianId");
	}

}
