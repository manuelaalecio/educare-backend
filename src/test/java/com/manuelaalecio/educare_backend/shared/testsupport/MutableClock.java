package com.manuelaalecio.educare_backend.shared.testsupport;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * UTC clock whose current instant can be changed by the test. {@link #withZone(ZoneId)} returns a view in another
 * time zone that keeps following the instant set here.
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
		return ZoneOffset.UTC.equals(zone) ? this : new ZonedView(this, zone);
	}

	private static final class ZonedView extends Clock {

		private final MutableClock source;
		private final ZoneId zone;

		private ZonedView(MutableClock source, ZoneId zone) {
			this.source = source;
			this.zone = zone;
		}

		@Override
		public Instant instant() {
			return source.instant();
		}

		@Override
		public ZoneId getZone() {
			return zone;
		}

		@Override
		public Clock withZone(ZoneId newZone) {
			return source.withZone(newZone);
		}

	}

}
