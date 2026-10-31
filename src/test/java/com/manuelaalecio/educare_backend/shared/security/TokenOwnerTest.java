package com.manuelaalecio.educare_backend.shared.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class TokenOwnerTest {

	private static final AuthenticatedUser USER =
			new AuthenticatedUser(UUID.fromString("0190f4a2-0000-7000-8000-000000000001"), "USER");
	private static final Instant CHANGED_AT = Instant.parse("2026-03-01T10:30:00Z");

	@Test
	void shouldAcceptAnyTokenWhenPasswordWasNeverChanged() {
		// given
		var owner = new TokenOwner(USER, null);

		// when / then
		assertThat(owner.accepts(Instant.parse("2020-01-01T00:00:00Z"))).isTrue();
		assertThat(owner.accepts(null)).isTrue();
	}

	@Test
	void shouldRejectTokenWhenIssuedBeforeLastPasswordChange() {
		// given
		var owner = new TokenOwner(USER, CHANGED_AT);

		// when
		boolean accepted = owner.accepts(CHANGED_AT.minusSeconds(1));

		// then
		assertThat(accepted).isFalse();
	}

	@Test
	void shouldAcceptTokenWhenIssuedInSameSecondAsLastPasswordChange() {
		// given
		var owner = new TokenOwner(USER, CHANGED_AT);

		// when
		boolean accepted = owner.accepts(CHANGED_AT);

		// then
		assertThat(accepted).isTrue();
	}

	@Test
	void shouldAcceptTokenWhenIssuedAfterLastPasswordChange() {
		// given
		var owner = new TokenOwner(USER, CHANGED_AT);

		// when
		boolean accepted = owner.accepts(CHANGED_AT.plusSeconds(1));

		// then
		assertThat(accepted).isTrue();
	}

	@Test
	void shouldRejectTokenWithoutIssueDateWhenPasswordWasChanged() {
		// given
		var owner = new TokenOwner(USER, CHANGED_AT);

		// when
		boolean accepted = owner.accepts(null);

		// then
		assertThat(accepted).isFalse();
	}

}
