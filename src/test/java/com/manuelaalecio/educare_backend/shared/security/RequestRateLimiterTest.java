package com.manuelaalecio.educare_backend.shared.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.manuelaalecio.educare_backend.shared.testsupport.MutableClock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class RequestRateLimiterTest {

	private static final Instant T0 = Instant.parse("2026-03-01T10:00:00Z");
	private static final UUID ANA = UUID.fromString("0190f4a2-0000-7000-8000-000000000001");
	private static final UUID BRUNO = UUID.fromString("0190f4a2-0000-7000-8000-000000000002");

	private final MutableClock clock = new MutableClock(T0);

	private final RequestRateLimiter limiter = new RequestRateLimiter(
			new RateLimitProperties(3, Duration.ofMinutes(1)), clock);

	private final Logger logger = (Logger) LoggerFactory.getLogger(RequestRateLimiter.class);

	private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

	@BeforeEach
	void setUp() {
		appender.start();
		logger.addAppender(appender);
	}

	@AfterEach
	void tearDown() {
		logger.detachAppender(appender);
	}

	@Test
	void shouldAcceptRequestsWhenWithinLimit() {
		// when / then
		assertThatCode(() -> acquireTimes(ANA, 3)).doesNotThrowAnyException();
		assertThat(appender.list).isEmpty();
	}

	@Test
	void shouldRefuseWithRetryAfterWhenLimitIsReached() {
		// given
		acquireTimes(ANA, 3);
		clock.setInstant(T0.plusSeconds(40));

		// when / then
		assertThatThrownBy(() -> limiter.acquire(ANA))
			.isInstanceOfSatisfying(RequestRateLimitExceededException.class, ex -> {
				assertThat(ex.getMessage()).isEqualTo("Muitas requisições. Aguarde um instante.");
				assertThat(ex.retryAfterSeconds()).hasValue(20);
			});
	}

	@Test
	void shouldRoundRetryAfterUpWhenRemainingTimeHasFraction() {
		// given
		acquireTimes(ANA, 3);
		clock.setInstant(T0.plusMillis(40_500));

		// when / then
		assertThatThrownBy(() -> limiter.acquire(ANA))
			.isInstanceOfSatisfying(RequestRateLimitExceededException.class,
					ex -> assertThat(ex.retryAfterSeconds()).hasValue(20));
	}

	@Test
	void shouldUseOneSecondRetryAfterWhenLessThanOneSecondRemains() {
		// given
		acquireTimes(ANA, 3);
		clock.setInstant(T0.plusMillis(59_999));

		// when / then
		assertThatThrownBy(() -> limiter.acquire(ANA))
			.isInstanceOfSatisfying(RequestRateLimitExceededException.class,
					ex -> assertThat(ex.retryAfterSeconds()).hasValue(1));
	}

	@Test
	void shouldReleaseWhenOldestRequestLeavesWindowExactly() {
		// given: 1 at 10:00:00 and 2 at 10:00:30, as in the scenario "Janela deslizante libera o usuário"
		limiter.acquire(ANA);
		clock.setInstant(T0.plusSeconds(30));
		acquireTimes(ANA, 2);

		// when
		clock.setInstant(T0.plusSeconds(59));
		Throwable refused = catchRefusal(ANA);
		clock.setInstant(T0.plusSeconds(60));

		// then
		assertThat(refused).isInstanceOfSatisfying(RequestRateLimitExceededException.class,
				ex -> assertThat(ex.retryAfterSeconds()).hasValue(1));
		assertThatCode(() -> limiter.acquire(ANA)).doesNotThrowAnyException();
		// the two of 10:00:30 still count
		assertThat(catchRefusal(ANA)).isInstanceOf(RequestRateLimitExceededException.class);
	}

	@Test
	void shouldNotCountRefusedRequestsWhenUserRetriesWhileBlocked() {
		// given
		acquireTimes(ANA, 3);
		clock.setInstant(T0.plusSeconds(30));
		for (int i = 0; i < 50; i++) {
			assertThat(catchRefusal(ANA)).isInstanceOf(RequestRateLimitExceededException.class);
		}

		// when
		clock.setInstant(T0.plusSeconds(60));

		// then: the 3 accepted left the window, and the 50 refused never counted
		assertThatCode(() -> acquireTimes(ANA, 3)).doesNotThrowAnyException();
	}

	@Test
	void shouldNotAffectOtherUserWhenOneIsBlocked() {
		// given
		acquireTimes(ANA, 3);

		// when / then
		assertThat(catchRefusal(ANA)).isInstanceOf(RequestRateLimitExceededException.class);
		assertThatCode(() -> acquireTimes(BRUNO, 3)).doesNotThrowAnyException();
	}

	@Test
	void shouldWarnOncePerBlockWithUserIdOnly() {
		// given
		acquireTimes(ANA, 3);

		// when: two refusals in one block, a release, and a new block
		catchRefusal(ANA);
		catchRefusal(ANA);
		clock.setInstant(T0.plusSeconds(60));
		acquireTimes(ANA, 3);
		catchRefusal(ANA);

		// then
		assertThat(appender.list).hasSize(2).allSatisfy(event -> {
			assertThat(event.getLevel()).isEqualTo(Level.WARN);
			assertThat(event.getFormattedMessage()).isEqualTo("Requisições bloqueadas por excesso: userId=" + ANA);
		});
	}

	@Test
	void shouldAcceptExactlyLimitWhenSameUserRequestsConcurrently() throws Exception {
		// given
		RequestRateLimiter concurrentLimiter = new RequestRateLimiter(
				new RateLimitProperties(10, Duration.ofMinutes(1)), clock);
		int threads = 50;
		ExecutorService executor = Executors.newFixedThreadPool(threads);
		CountDownLatch start = new CountDownLatch(1);
		List<Future<Boolean>> results = new ArrayList<>();
		try {
			for (int i = 0; i < threads; i++) {
				results.add(executor.submit(() -> {
					start.await();
					try {
						concurrentLimiter.acquire(ANA);
						return true;
					}
					catch (RequestRateLimitExceededException ex) {
						return false;
					}
				}));
			}

			// when
			start.countDown();
			int accepted = 0;
			for (Future<Boolean> result : results) {
				accepted += result.get() ? 1 : 0;
			}

			// then
			assertThat(accepted).isEqualTo(10);
		}
		finally {
			executor.shutdownNow();
		}
	}

	private void acquireTimes(UUID userId, int times) {
		for (int i = 0; i < times; i++) {
			limiter.acquire(userId);
		}
	}

	private Throwable catchRefusal(UUID userId) {
		try {
			limiter.acquire(userId);
			return null;
		}
		catch (RequestRateLimitExceededException ex) {
			return ex;
		}
	}

}
