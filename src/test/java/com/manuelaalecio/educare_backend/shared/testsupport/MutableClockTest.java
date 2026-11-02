package com.manuelaalecio.educare_backend.shared.testsupport;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

class MutableClockTest {

	private static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");

	@Test
	void shouldUseRequestedZoneWhenViewedInAnotherZone() {
		// given: 01:00 UTC on March 2nd is still March 1st in São Paulo
		var clock = new MutableClock(Instant.parse("2026-03-02T01:00:00Z"));

		// when
		Clock saoPaulo = clock.withZone(SAO_PAULO);

		// then
		assertThat(saoPaulo.getZone()).isEqualTo(SAO_PAULO);
		assertThat(LocalDate.now(saoPaulo)).isEqualTo(LocalDate.of(2026, 3, 1));
		assertThat(LocalDate.now(clock)).isEqualTo(LocalDate.of(2026, 3, 2));
	}

	@Test
	void shouldFollowNewInstantWhenViewIsCreatedBeforeTheChange() {
		// given
		var clock = new MutableClock(Instant.parse("2026-03-01T10:00:00Z"));
		Clock saoPaulo = clock.withZone(SAO_PAULO);

		// when
		clock.setInstant(Instant.parse("2026-03-05T10:00:00Z"));

		// then
		assertThat(saoPaulo.instant()).isEqualTo(Instant.parse("2026-03-05T10:00:00Z"));
	}

	@Test
	void shouldReturnItselfWhenViewedInUtcAgain() {
		// given
		var clock = new MutableClock(Instant.parse("2026-03-01T10:00:00Z"));

		// when / then
		assertThat(clock.withZone(ZoneOffset.UTC)).isSameAs(clock);
		assertThat(clock.withZone(SAO_PAULO).withZone(ZoneOffset.UTC)).isSameAs(clock);
	}

}
