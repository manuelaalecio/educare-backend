package com.manuelaalecio.educare_backend.shared.security;

import java.util.Optional;
import java.util.UUID;

/**
 * Finds the current state of the user of a token. Implemented by the module that owns the users, so
 * {@code shared} does not depend on it. Called on every authenticated request, so it must read the user only once.
 */
public interface AuthenticatedUserLookup {

	Optional<TokenOwner> findById(UUID id);

}
