package com.manuelaalecio.educare_backend.user.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.manuelaalecio.educare_backend.user.domain.TooManyPasswordAttemptsException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Limits the wrong current passwords when users change their own password: with {@value #MAX_FAILURES} failures in
 * the last {@link #WINDOW} (a sliding window), the user is blocked until enough of them leave the window; a
 * successful change clears the count. Kept in memory, per user: a single instance serves the API, and losing the
 * count on restart is acceptable. Logs carry only the user id, never the password, its hash nor the login.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PasswordAttemptLimiter {

	static final int MAX_FAILURES = 5;

	static final Duration WINDOW = Duration.ofMinutes(15);

	private final Clock clock;

	/** Instants of the recent failures of each user; a user without recent failures has no entry. */
	private final Map<UUID, Deque<Instant>> failures = new ConcurrentHashMap<>();

	/**
	 * @throws TooManyPasswordAttemptsException when the user has {@value #MAX_FAILURES} or more failures in the window
	 */
	public void ensureAllowed(UUID userId) {
		Instant now = clock.instant();
		Deque<Instant> recent = failures.computeIfPresent(userId, (id, instants) -> pruned(instants, now));
		if (recent != null && recent.size() >= MAX_FAILURES) {
			log.warn("Troca de senha bloqueada por excesso de tentativas: userId={}", userId);
			throw new TooManyPasswordAttemptsException();
		}
	}

	public void recordFailure(UUID userId) {
		Instant now = clock.instant();
		failures.compute(userId, (id, instants) -> {
			Deque<Instant> recent = instants == null ? null : pruned(instants, now);
			if (recent == null) {
				recent = new ArrayDeque<>();
			}
			recent.addLast(now);
			return recent;
		});
	}

	public void reset(UUID userId) {
		failures.remove(userId);
	}

	/**
	 * Drops the failures that left the window: a failure stops counting exactly {@link #WINDOW} after it happened.
	 *
	 * @return {@code null} when no failure is left, which removes the user's entry
	 */
	private static Deque<Instant> pruned(Deque<Instant> instants, Instant now) {
		Instant oldestCounted = now.minus(WINDOW);
		while (!instants.isEmpty() && !instants.peekFirst().isAfter(oldestCounted)) {
			instants.removeFirst();
		}
		return instants.isEmpty() ? null : instants;
	}

}
