package com.manuelaalecio.educare_backend.shared.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import lombok.extern.slf4j.Slf4j;

/**
 * Limits the requests of each authenticated user to {@link RateLimitProperties#maxRequests()} in the last
 * {@link RateLimitProperties#window()}, a sliding window: a request stops counting exactly one window after it was
 * accepted. Refused requests are not counted, so retrying while blocked does not extend the block. Kept in memory, per
 * user: a single instance serves the API, and losing the count on restart is acceptable. Logs carry only the user id,
 * once per block.
 * <p>
 * A user keeps an entry (at most {@code maxRequests} instants) after they stop using the API; the users are the staff
 * of one institution, so the map stays small.
 */
@Slf4j
public class RequestRateLimiter {

	private final int maxRequests;

	private final Duration window;

	private final Clock clock;

	private final Map<UUID, UserWindow> windows = new ConcurrentHashMap<>();

	public RequestRateLimiter(RateLimitProperties properties, Clock clock) {
		this.maxRequests = properties.maxRequests();
		this.window = properties.window();
		this.clock = clock;
	}

	/**
	 * Counts a request of the user, or refuses it without counting.
	 *
	 * @throws RequestRateLimitExceededException when the user already has {@code maxRequests} requests in the window
	 */
	public void acquire(UUID userId) {
		Instant now = clock.instant();
		Decision[] decision = new Decision[1];
		windows.compute(userId, (id, current) -> {
			UserWindow userWindow = current == null ? new UserWindow() : current;
			decision[0] = userWindow.acquire(now);
			return userWindow;
		});
		Decision result = decision[0];
		if (result.accepted()) {
			return;
		}
		if (result.firstRefusal()) {
			log.warn("Requisições bloqueadas por excesso: userId={}", userId);
		}
		throw new RequestRateLimitExceededException(result.retryAfterSeconds());
	}

	/** Whole seconds until {@code instant}, rounded up, at least 1. */
	private static long secondsUntil(Instant instant, Instant now) {
		Duration remaining = Duration.between(now, instant);
		long seconds = remaining.getSeconds() + (remaining.getNano() > 0 ? 1 : 0);
		return Math.max(1, seconds);
	}

	private record Decision(boolean accepted, boolean firstRefusal, long retryAfterSeconds) {

		static final Decision ACCEPTED = new Decision(true, false, 0);

	}

	/** The accepted requests of one user still in the window; changed only inside {@code compute}. */
	private final class UserWindow {

		private final Deque<Instant> accepted = new ArrayDeque<>();

		/** Whether the last request was refused, so that a block is logged once. */
		private boolean blocked;

		Decision acquire(Instant now) {
			Instant oldestCounted = now.minus(window);
			while (!accepted.isEmpty() && !accepted.peekFirst().isAfter(oldestCounted)) {
				accepted.removeFirst();
			}
			if (accepted.size() < maxRequests) {
				accepted.addLast(now);
				blocked = false;
				return Decision.ACCEPTED;
			}
			boolean firstRefusal = !blocked;
			blocked = true;
			return new Decision(false, firstRefusal, secondsUntil(accepted.peekFirst().plus(window), now));
		}

	}

}
