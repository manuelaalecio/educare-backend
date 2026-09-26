package com.manuelaalecio.educare_backend.shared.security;

import java.util.Optional;
import java.util.UUID;

/**
 * Finds the current state of the user of a token. Implemented by the module that owns the users, so
 * {@code shared} does not depend on it.
 */
public interface AuthenticatedUserLookup {

	Optional<AuthenticatedUser> findById(UUID id);

}
