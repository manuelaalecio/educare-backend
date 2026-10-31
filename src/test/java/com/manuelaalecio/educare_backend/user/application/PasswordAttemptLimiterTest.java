package com.manuelaalecio.educare_backend.user.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import com.manuelaalecio.educare_backend.shared.testsupport.MutableClock;
import com.manuelaalecio.educare_backend.user.domain.TooManyPasswordAttemptsException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
class PasswordAttemptLimiterTest {

	private static final UUID ANA = UUID.fromString("0190b6f0-0000-7000-8000-000000000001");
	private static final UUID BRUNO = UUID.fromString("0190b6f0-0000-7000-8000-000000000002");
	private static final Instant START = Instant.parse("2026-03-01T10:00:00Z");

	private final MutableClock clock = new MutableClock(START);
	private final PasswordAttemptLimiter limiter = new PasswordAttemptLimiter(clock);

	/** Records one failure per minute, starting at {@link #START}. */
	private void failEveryMinute(UUID userId, int failures) {
		for (int i = 0; i < failures; i++) {
			clock.setInstant(START.plus(Duration.ofMinutes(i)));
			limiter.recordFailure(userId);
		}
	}

	@Test
	void shouldAllowWhenUserHasNoFailures() {
		assertThatCode(() -> limiter.ensureAllowed(ANA)).doesNotThrowAnyException();
	}

	@Test
	void shouldAllowWhenUserHasFourFailuresInWindow() {
		// given
		failEveryMinute(ANA, 4);

		// when / then
		assertThatCode(() -> limiter.ensureAllowed(ANA)).doesNotThrowAnyException();
	}

	@Test
	void shouldBlockWhenUserHasFiveFailuresInWindow() {
		// given
		failEveryMinute(ANA, 5);

		// when / then
		assertThatThrownBy(() -> limiter.ensureAllowed(ANA))
				.isInstanceOf(TooManyPasswordAttemptsException.class)
				.hasMessage("Muitas tentativas. Tente novamente em alguns minutos.");
	}

	@Test
	void shouldStopCountingFailureExactlyFifteenMinutesAfterIt() {
		// given failures at 10:00, 10:01, 10:02, 10:03 and 10:04
		failEveryMinute(ANA, 5);

		// when / then
		clock.setInstant(START.plus(Duration.ofMinutes(15)).minusSeconds(1));
		assertThatThrownBy(() -> limiter.ensureAllowed(ANA)).isInstanceOf(TooManyPasswordAttemptsException.class);
		clock.setInstant(START.plus(Duration.ofMinutes(15)));
		assertThatCode(() -> limiter.ensureAllowed(ANA)).doesNotThrowAnyException();
	}

	@Test
	void shouldBlockAgainWhenNewFailureFillsSlidingWindow() {
		// given failures at 10:00..10:04; at 10:15 the first one left the window
		failEveryMinute(ANA, 5);
		clock.setInstant(START.plus(Duration.ofMinutes(15)));

		// when
		limiter.recordFailure(ANA);

		// then
		assertThatThrownBy(() -> limiter.ensureAllowed(ANA)).isInstanceOf(TooManyPasswordAttemptsException.class);
	}

	@Test
	void shouldStartNewCountWhenAllFailuresLeftWindow() {
		// given
		failEveryMinute(ANA, 5);
		clock.setInstant(START.plus(Duration.ofHours(1)));

		// when
		for (int i = 0; i < 4; i++) {
			limiter.recordFailure(ANA);
		}

		// then
		assertThatCode(() -> limiter.ensureAllowed(ANA)).doesNotThrowAnyException();
	}

	@Test
	void shouldAllowWhenCountWasReset() {
		// given
		failEveryMinute(ANA, 5);

		// when
		limiter.reset(ANA);

		// then
		assertThatCode(() -> limiter.ensureAllowed(ANA)).doesNotThrowAnyException();
	}

	@Test
	void shouldCountFailuresOfEachUserSeparately() {
		// given
		failEveryMinute(ANA, 5);

		// when / then
		assertThatCode(() -> limiter.ensureAllowed(BRUNO)).doesNotThrowAnyException();
	}

	@Test
	void shouldNotRecordFailureWhenAttemptIsBlocked() {
		// given failures at 10:00..10:04, and blocked attempts at 10:10
		failEveryMinute(ANA, 5);
		clock.setInstant(START.plus(Duration.ofMinutes(10)));
		for (int i = 0; i < 3; i++) {
			assertThatThrownBy(() -> limiter.ensureAllowed(ANA))
					.isInstanceOf(TooManyPasswordAttemptsException.class);
		}

		// when the first failure leaves the window
		clock.setInstant(START.plus(Duration.ofMinutes(15)));

		// then the blocked attempts did not count
		assertThatCode(() -> limiter.ensureAllowed(ANA)).doesNotThrowAnyException();
	}

	@Test
	void shouldLogWarningWithOnlyUserIdWhenAttemptIsBlocked(CapturedOutput output) {
		// given
		failEveryMinute(ANA, 5);

		// when
		assertThatThrownBy(() -> limiter.ensureAllowed(ANA)).isInstanceOf(TooManyPasswordAttemptsException.class);

		// then
		assertThat(output.getAll())
				.contains("WARN")
				.contains("Troca de senha bloqueada por excesso de tentativas: userId=" + ANA);
	}

	@Test
	void shouldNotLogWhenAttemptIsAllowed(CapturedOutput output) {
		// given
		failEveryMinute(ANA, 4);

		// when
		limiter.ensureAllowed(ANA);

		// then
		assertThat(output.getAll()).doesNotContain("Troca de senha bloqueada");
	}

}
