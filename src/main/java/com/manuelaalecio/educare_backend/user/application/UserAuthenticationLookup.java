package com.manuelaalecio.educare_backend.user.application;

import java.util.Optional;
import java.util.UUID;

import com.manuelaalecio.educare_backend.shared.security.AuthenticatedUser;
import com.manuelaalecio.educare_backend.shared.security.AuthenticatedUserLookup;
import com.manuelaalecio.educare_backend.shared.security.TokenOwner;
import com.manuelaalecio.educare_backend.user.domain.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gives {@code shared/security} the current state of the user of a token, read from the database on every
 * request, so a deleted or demoted user loses access right away, and so does every token issued before the last
 * password change. A single read of the user serves both.
 */
@Component
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class UserAuthenticationLookup implements AuthenticatedUserLookup {

	private final UserRepository userRepository;

	@Override
	public Optional<TokenOwner> findById(UUID id) {
		return userRepository.findById(id).map(user -> new TokenOwner(
				new AuthenticatedUser(user.getId(), user.getRole().name()), user.getPasswordChangedAt()));
	}

}
