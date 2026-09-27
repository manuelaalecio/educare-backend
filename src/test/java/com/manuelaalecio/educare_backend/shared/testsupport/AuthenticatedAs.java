package com.manuelaalecio.educare_backend.shared.testsupport;

import java.util.UUID;
import java.util.function.Supplier;

import com.manuelaalecio.educare_backend.shared.security.AuthenticatedUser;
import com.manuelaalecio.educare_backend.shared.security.AuthenticatedUserAuthentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Runs code as an authenticated user, so records saved outside a request ({@code @DataJpaTest}, service tests) get
 * that user as author, as they would in a request with a token (design D4 of the add-record-authorship change). The
 * previous security context is restored afterwards, even when the code throws.
 *
 * <pre>
 * AuthenticatedAs.run(anaId, () -&gt; repository.saveAndFlush(entity));
 * Child saved = AuthenticatedAs.call(anaId, () -&gt; service.create(command));
 * </pre>
 */
public final class AuthenticatedAs {

	/** The role does not matter for authorship; only the id is recorded. */
	private static final String ROLE = "USER";

	private AuthenticatedAs() {
	}

	public static void run(UUID userId, Runnable action) {
		call(userId, () -> {
			action.run();
			return null;
		});
	}

	public static <T> T call(UUID userId, Supplier<T> action) {
		SecurityContext previous = SecurityContextHolder.getContext();
		SecurityContext context = SecurityContextHolder.createEmptyContext();
		context.setAuthentication(new AuthenticatedUserAuthentication(new AuthenticatedUser(userId, ROLE)));
		SecurityContextHolder.setContext(context);
		try {
			return action.get();
		}
		finally {
			SecurityContextHolder.setContext(previous);
		}
	}

}
