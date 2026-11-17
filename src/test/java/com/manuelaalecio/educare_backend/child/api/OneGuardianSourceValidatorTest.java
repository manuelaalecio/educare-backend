package com.manuelaalecio.educare_backend.child.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import com.manuelaalecio.educare_backend.child.api.dto.ChildGuardianRequest;
import com.manuelaalecio.educare_backend.child.api.dto.NewGuardianRequest;
import org.junit.jupiter.api.Test;

class OneGuardianSourceValidatorTest {

	private static final UUID MARIA_ID = UUID.fromString("0190f4a2-0000-7000-8000-0000000000d1");
	private static final NewGuardianRequest MARIA = new NewGuardianRequest("Maria Souza", null, null, null, null,
			"11912345678", true, null, null, null);

	private final OneGuardianSourceValidator validator = new OneGuardianSourceValidator();

	@Test
	void shouldAcceptItemWhenNull() {
		assertThat(validator.isValid(null, null)).isTrue();
	}

	@Test
	void shouldAcceptItemWhenItHasOnlyGuardianId() {
		assertThat(validator.isValid(new ChildGuardianRequest(MARIA_ID, null, "MOTHER"), null)).isTrue();
	}

	@Test
	void shouldAcceptItemWhenItHasOnlyNewGuardian() {
		assertThat(validator.isValid(new ChildGuardianRequest(null, MARIA, "MOTHER"), null)).isTrue();
	}

	@Test
	void shouldRejectItemWhenItHasNoSource() {
		assertThat(validator.isValid(new ChildGuardianRequest(null, null, "MOTHER"), null)).isFalse();
	}

	@Test
	void shouldRejectItemWhenItHasBothSources() {
		assertThat(validator.isValid(new ChildGuardianRequest(MARIA_ID, MARIA, "MOTHER"), null)).isFalse();
	}

}
