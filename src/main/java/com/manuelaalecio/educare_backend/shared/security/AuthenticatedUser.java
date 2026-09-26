package com.manuelaalecio.educare_backend.shared.security;

import java.util.UUID;

/**
 * The principal of an authenticated request: the user of the token, with the role they have now.
 *
 * @param role the role name as stored (e.g. {@code ADMIN}), without the {@code ROLE_} prefix
 */
public record AuthenticatedUser(UUID id, String role) {
}
