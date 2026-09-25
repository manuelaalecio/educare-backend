package com.manuelaalecio.educare_backend.shared.testsupport;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * UTC clock whose current instant can be changed by the test.
 */
public class MutableClock extends Clock {

	private volatile Instant instant;

	public MutableClock(Instant initial) {
		this.instant = initial;
	}

	public void setInstant(Instant instant) {
		this.instant = instant;
	}

	@Override
	public Instant instant() {
		return instant;
	}

	@Override
	public ZoneId getZone() {
		return ZoneOffset.UTC;
	}

	@Override
	public Clock withZone(ZoneId zone) {
		return this;
	}

}
