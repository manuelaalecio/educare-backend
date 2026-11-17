package com.manuelaalecio.educare_backend.child.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import com.manuelaalecio.educare_backend.child.api.dto.ChildGuardianRequest;
import com.manuelaalecio.educare_backend.child.api.dto.NewGuardianRequest;
import org.junit.jupiter.api.Test;

class UniqueGuardianIdsValidatorTest {

	private static final UUID MARIA_ID = UUID.fromString("0190f4a2-0000-7000-8000-0000000000d1");
	private static final UUID JOAO_ID = UUID.fromString("0190f4a2-0000-7000-8000-0000000000d2");
	private static final NewGuardianRequest NEW = new NewGuardianRequest("Maria Souza", null, null, null, null,
			"11912345678", true, null, null, null);

	private final UniqueGuardianIdsValidator validator = new UniqueGuardianIdsValidator();

	@Test
	void shouldAcceptListWhenNull() {
		assertThat(validator.isValid(null, null)).isTrue();
	}

	@Test
	void shouldAcceptListWhenIdsAreDistinctAndNewGuardiansAndNullItemsAreIgnored() {
		// given
		List<ChildGuardianRequest> items = Arrays.asList(new ChildGuardianRequest(MARIA_ID, null, "MOTHER"),
				new ChildGuardianRequest(JOAO_ID, null, "FATHER"), new ChildGuardianRequest(null, NEW, "AUNT"),
				new ChildGuardianRequest(null, NEW, "UNCLE"), null);

		// when / then
		assertThat(validator.isValid(items, null)).isTrue();
	}

	@Test
	void shouldRejectListWhenIdIsRepeated() {
		// given
		List<ChildGuardianRequest> items = List.of(new ChildGuardianRequest(MARIA_ID, null, "MOTHER"),
				new ChildGuardianRequest(JOAO_ID, null, "FATHER"), new ChildGuardianRequest(MARIA_ID, null, "OTHER"));

		// when / then
		assertThat(validator.isValid(items, null)).isFalse();
	}

}
