package com.manuelaalecio.educare_backend.shared.security;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.AuditorAware;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * The author of the records saved now: the id of the {@link AuthenticatedUser} of the current request. Without one
 * (no authentication, anonymous, or another kind of principal), records are saved without author.
 */
public class AuthenticatedUserAuditor implements AuditorAware<UUID> {

	@Override
	public Optional<UUID> getCurrentAuditor() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user) {
			return Optional.of(user.id());
		}
		return Optional.empty();
	}

}
