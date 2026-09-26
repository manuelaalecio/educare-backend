package com.manuelaalecio.educare_backend.user.application;

import java.util.Optional;
import java.util.UUID;

import com.manuelaalecio.educare_backend.shared.security.AuthenticatedUser;
import com.manuelaalecio.educare_backend.shared.security.AuthenticatedUserLookup;
import com.manuelaalecio.educare_backend.user.domain.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gives {@code shared/security} the current state of the user of a token, read from the database on every
 * request, so a deleted or demoted user loses access right away.
 */
@Component
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class UserAuthenticationLookup implements AuthenticatedUserLookup {

	private final UserRepository userRepository;

	@Override
	public Optional<AuthenticatedUser> findById(UUID id) {
		return userRepository.findById(id).map(user -> new AuthenticatedUser(user.getId(), user.getRole().name()));
	}

}
