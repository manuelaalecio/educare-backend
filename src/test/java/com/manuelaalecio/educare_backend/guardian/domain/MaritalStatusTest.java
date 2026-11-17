package com.manuelaalecio.educare_backend.guardian.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

class MaritalStatusTest {

	@Test
	void shouldHaveSameConstantsAsChildMaritalStatusWhenCompared() {
		assertThat(Arrays.stream(MaritalStatus.values()).map(Enum::name))
			.containsExactlyElementsOf(Arrays.stream(com.manuelaalecio.educare_backend.child.domain.MaritalStatus.values())
				.map(Enum::name)
				.toList());
	}

}
