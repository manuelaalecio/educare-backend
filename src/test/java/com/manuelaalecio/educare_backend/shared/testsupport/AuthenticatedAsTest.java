package com.manuelaalecio.educare_backend.shared.testsupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import com.manuelaalecio.educare_backend.shared.security.AuthenticatedUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

class AuthenticatedAsTest {

	private static final UUID ANA_ID = UUID.fromString("0190f4a2-0000-7000-8000-000000000001");

	@AfterEach
	void clearContext() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void shouldAuthenticateUserWhileRunning() {
		// given
		AtomicReference<Authentication> seen = new AtomicReference<>();

		// when
		AuthenticatedAs.run(ANA_ID, () -> seen.set(SecurityContextHolder.getContext().getAuthentication()));

		// then
		assertThat(seen.get()).isNotNull();
		assertThat(seen.get().isAuthenticated()).isTrue();
		assertThat(seen.get().getPrincipal()).isInstanceOfSatisfying(AuthenticatedUser.class,
				user -> assertThat(user.id()).isEqualTo(ANA_ID));
	}

	@Test
	void shouldReturnResultAndAuthenticateUserWhileCalling() {
		// when
		UUID principalId = AuthenticatedAs.call(ANA_ID,
				() -> ((AuthenticatedUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal()).id());

		// then
		assertThat(principalId).isEqualTo(ANA_ID);
	}

	@Test
	void shouldRestorePreviousContextWhenExecutionEnds() {
		// given
		SecurityContext previous = SecurityContextHolder.getContext();

		// when
		AuthenticatedAs.run(ANA_ID, () -> { });

		// then
		assertThat(SecurityContextHolder.getContext()).isSameAs(previous);
		assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
	}

	@Test
	void shouldRestorePreviousContextWhenExecutionThrows() {
		// given
		SecurityContext previous = SecurityContextHolder.getContext();

		// when / then
		assertThatThrownBy(() -> AuthenticatedAs.call(ANA_ID, () -> {
			throw new IllegalStateException("boom");
		})).isInstanceOf(IllegalStateException.class).hasMessage("boom");
		assertThat(SecurityContextHolder.getContext()).isSameAs(previous);
		assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
	}

}
